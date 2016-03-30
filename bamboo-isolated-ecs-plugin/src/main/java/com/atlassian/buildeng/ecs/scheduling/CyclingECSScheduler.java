package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;

public class CyclingECSScheduler implements ECSScheduler, DisposableBean {
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One (1) week
    static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMinutes(5); // Give instances 5 minutes to pick up a task
    static final double DEFAULT_HIGH_WATERMARK = 0.9; // Scale when cluster is at 90% of maximum capacity
    static final String DEFAULT_ASG_NAME = "Staging Bamboo ECS";

    private final Duration stalePeriod;
    private final Duration gracePeriod;
    private final double highWatermark;
    private final String asgName;
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);
    //for tests
    final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<Request> requests = new ArrayList<>();
    
    private final SchedulerBackend schedulerBackend;

    public CyclingECSScheduler(SchedulerBackend schedulerBackend) {
        stalePeriod = DEFAULT_STALE_PERIOD;
        gracePeriod = DEFAULT_GRACE_PERIOD;
        highWatermark = DEFAULT_HIGH_WATERMARK;
        asgName = DEFAULT_ASG_NAME;
        this.schedulerBackend = schedulerBackend;
        executor.submit(new EndlessPolling());
    }

    // Get the instance models of the given instance ARNs
    List<DockerHost> getDockerHosts(List<ContainerInstance> containerInstances) throws ECSException {
        List<Instance> instances = schedulerBackend.getInstances(containerInstances);
        // Match up container instances and EC2 instances by instance id
        instances.sort((o1, o2) -> o1.getInstanceId().compareTo(o2.getInstanceId()));
        containerInstances.sort((o1, o2) -> o1.getEc2InstanceId().compareTo(o2.getEc2InstanceId()));

        int iSize = instances.size();
        int ciSize = containerInstances.size();

        List<DockerHost> dockerHosts = new ArrayList<>();

        if (iSize != ciSize){
            logger.warn(String.format("Scheduler got different lengths for instances (%d) and container instances (%d)", iSize, ciSize));
        } else {
            for (int i = 0; i < ciSize; i++) {
                dockerHosts.add(new DockerHost(containerInstances.get(i), instances.get(i)));
            }
        }
        return dockerHosts;
    }

    // Select the best host to run a task with the given required resources out of a list of candidates
    // Is Nothing if there are no feasible hosts
    static Optional<DockerHost> selectHost(List<DockerHost> candidates, int requiredMemory, int requiredCpu) {
        return candidates.stream()
            .filter(dockerHost -> dockerHost.canRun(requiredMemory, requiredCpu))
            .sorted(DockerHost.compareByResources())
            .findFirst();
    }

    private Map<Boolean, List<DockerHost>> partitionFreshness (List<DockerHost> dockerHosts, Duration stalePeriod) {
        // Java pls
        return dockerHosts.stream()
                .filter(DockerHost::getAgentConnected)
                .collect(Collectors.partitioningBy(dockerHost -> dockerHost.ageMillis() < stalePeriod.toMillis()));
    }

    private Map<Boolean, List<DockerHost>> partitionFreshness (List<DockerHost> dockerHosts) {
        return partitionFreshness(dockerHosts, stalePeriod);
    }

    /**
     * Stream stale hosts not running any tasks
     */
    private List<DockerHost> unusedStaleInstances(List<DockerHost> staleHosts) {
        return staleHosts.stream()
                .filter(DockerHost::runningNothing)
                .collect(Collectors.toList());
    }

    private List<DockerHost> unusedFreshInstances(List<DockerHost> freshHosts, final Set<DockerHost> usedCandidates) {
        return freshHosts.stream()
                .filter(dockerHost -> !usedCandidates.contains(dockerHost))
                .filter(DockerHost::runningNothing)
                .filter(dockerHost -> dockerHost.ageMillis() > gracePeriod.toMillis())
                .collect(Collectors.toList());
    }

    // Scale up if capacity is near full
    static double percentageUtilized(List<DockerHost> freshHosts) {
        double clusterRegisteredCPU = freshHosts.stream().mapToInt(DockerHost::getRegisteredCpu).sum();
        double clusterRemainingCPU = freshHosts.stream().mapToInt(DockerHost::getRemainingCpu).sum();
        if (clusterRegisteredCPU == 0) {
            return 1;
        } else {
            return 1 - (clusterRemainingCPU / clusterRegisteredCPU);
        }
    }

    @Override
    public String schedule(String cluster, int requiredMemory, int requiredCpu) throws ECSException {
        try {
            return scheduleImpl(cluster, requiredMemory, requiredCpu).get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new ECSException(ex);
        }
    }

    //for tests
    Future<String> scheduleImpl(String cluster, int requiredMemory, int requiredCpu) throws ECSException {
        SettableFuture<String> result = new SettableFuture<>();
        synchronized (requests) {
            requests.add(new Request(cluster, requiredCpu, requiredMemory, result));
            requests.notifyAll();
        }
        return result;
    }
    
    
    private Request pollRequest() {
        Request request = null;
        synchronized (requests) {
            if (!requests.isEmpty()) {
                request = requests.remove(0);
            }
        }
        return request;
    }
    
    private void processRequests() {
        Request request = pollRequest();
        if (request == null) return;
        String cluster = request.getCluster();
        final List<DockerHost> dockerHosts;
        try {
            //this can take time (network) and in the meantime other requests can accumulate.
            List<ContainerInstance> containerInstances = schedulerBackend.getClusterContainerInstances(cluster);
            dockerHosts = getDockerHosts(containerInstances);
        } catch (ECSException ex) {
            //mark all futures with exception.. and let the clients wait and retry..
            while (request != null) {
                request.getFuture().setException(ex);
                request = pollRequest();
            }
            logger.error("Cannot query cluster " + cluster + " containers", ex);
            return;
        }
        int currentSize = dockerHosts.size();
        final Map<Boolean, List<DockerHost>> partitionedHosts = partitionFreshness(dockerHosts);
        final List<DockerHost> freshHosts = partitionedHosts.get(true);
        final List<DockerHost> staleHosts = partitionedHosts.get(false);
        final Set<DockerHost> usedCandidates = new HashSet<>();
        boolean someDiscarded = false;
        while (request != null) {
            logger.debug("Processing request for {}", request);
            if (!cluster.equals(request.getCluster())) {
                //we need to save current cluster.. new items arrived for different one.
                request.getFuture().setException(new ECSException("Different cluster processed now."));
                logger.info("Skipped processing due to multiple clusters in queue");
                break;
            }
            Optional<DockerHost> candidate = selectHost(freshHosts, request.getMemory(), request.getCpu());
            if (candidate.isPresent()) {
                DockerHost candidateHost = candidate.get();
                usedCandidates.add(candidateHost);
                candidateHost.reduceAvailableCpuBy(request.getCpu());
                candidateHost.reduceAvailableMemoryBy(request.getMemory());
                request.getFuture().set(candidateHost.getContainerInstanceArn());
            } else {
                //scale up + down and set all other queued requests to null.
                request.getFuture().setException(new ECSException("Capacity not available"));
                someDiscarded = true;
            }
            request = pollRequest();
        }
        
        //see if we need to scale up or down..
        List<DockerHost> unusedStales = unusedStaleInstances(staleHosts);
        List<DockerHost> unusedFresh = unusedFreshInstances(freshHosts, usedCandidates);
        List<DockerHost> usedFresh = new ArrayList<>(freshHosts);
        usedFresh.removeAll(unusedFresh);
        int desiredScaleSize = currentSize;
        //calculate usage from used fresh instances only
        if (someDiscarded || percentageUtilized(usedFresh) >= highWatermark) {
            //if there are no unused fresh ones, scale up
            desiredScaleSize = desiredScaleSize + 1;
        } 
        List<String> toTerminate = Stream.concat(unusedStales.stream(), unusedFresh.stream())
                .map(DockerHost::getInstanceId)
                .collect(Collectors.toList());
        if (!toTerminate.isEmpty()) {
            desiredScaleSize = desiredScaleSize - toTerminate.size();
            //we are reducing the currentSize by the terminated list because that's
            //what the terminateInstances method should reduce it to.
            currentSize = currentSize - toTerminate.size();
            schedulerBackend.terminateInstances(toTerminate, asgName);
        }
        if (desiredScaleSize != currentSize) {
            try {
                schedulerBackend.scaleTo(desiredScaleSize, asgName);
            } catch (ECSException ex) {
                logger.error("Scaling of " + asgName + " failed", ex);
            }
        }
    }
    
    void shutdownExecutor() {
        executor.shutdown();
    }

    @Override
    public void destroy() throws Exception {
        shutdownExecutor();
    }

    private class EndlessPolling implements Runnable {

        public EndlessPolling() {
        }

        @Override
        public void run() {
            synchronized (requests) {
                if (requests.isEmpty()) {
                    try {
                        logger.debug("waiting for incoming requests");
                        requests.wait();
                    } catch (InterruptedException ex) {
                        logger.error("Polling Thread interrupted");
                    }
                }
            }
            processRequests();
            executor.submit(this);
        }

    }

    private static class Request {
        private final String cluster;
        private final int cpu;
        private final int memory;
        private final SettableFuture<String> future;

        public Request(String cluster, int cpu, int memory, SettableFuture<String> future) {
            this.cluster = cluster;
            this.cpu = cpu;
            this.memory = memory;
            this.future = future;
        }

        public String getCluster() {
            return cluster;
        }

        public int getCpu() {
            return cpu;
        }

        public int getMemory() {
            return memory;
        }

        public SettableFuture<String> getFuture() {
            return future;
        }
        
    }
}
