package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.Constants;
import com.atlassian.buildeng.ecs.GlobalConfiguration;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class CyclingECSScheduler implements ECSScheduler, DisposableBean {
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One (1) week
    private static final double DEFAULT_HIGH_WATERMARK = 0.9; // Scale when cluster is at 90% of maximum capacity

    private final Duration stalePeriod;
    private final double highWatermark;
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);
    private long lackingCPU = 0;
    private long lackingMemory = 0;
    private final Set<UUID> consideredRequestIdentifiers = new HashSet<>();
    @VisibleForTesting
    final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<Pair<SchedulingRequest, SchedulingCallback>> requests = new LinkedBlockingQueue<>();

    private final SchedulerBackend schedulerBackend;
    private final GlobalConfiguration globalConfiguration;

    public CyclingECSScheduler(SchedulerBackend schedulerBackend, GlobalConfiguration globalConfiguration) {
        stalePeriod = DEFAULT_STALE_PERIOD;
        highWatermark = DEFAULT_HIGH_WATERMARK;
        this.schedulerBackend = schedulerBackend;
        this.globalConfiguration = globalConfiguration;
        executor.submit(new EndlessPolling());
    }

    // Select the best host to run a task with the given required resources out of a list of candidates
    // Is Nothing if there are no feasible hosts
    static Optional<DockerHost> selectHost(Collection<DockerHost> candidates, int requiredMemory, int requiredCpu) {
        return candidates.stream()
                .filter(dockerHost -> dockerHost.canRun(requiredMemory, requiredCpu))
                .sorted(DockerHost.compareByResources())
                .findFirst();
    }


    // Scale up if capacity is near full
    static double percentageUtilized(List<DockerHost> freshHosts) {
        double clusterRegisteredCPU = freshHosts.stream().mapToInt(DockerHost::getRegisteredCpu).sum();
        double clusterRemainingCPU = freshHosts.stream().mapToInt(DockerHost::getRemainingCpu).sum();
        if (clusterRegisteredCPU == 0) {
            return 1;
        } else {
            return 1 - clusterRemainingCPU / clusterRegisteredCPU;
        }
    }

    @Override
    public void schedule(SchedulingRequest request, SchedulingCallback callback) {
        requests.add(Pair.of(request, callback));
    }

    private void processRequests(Pair<SchedulingRequest, SchedulingCallback> pair) {
        if (pair == null) return;
        SchedulingRequest request = pair.getLeft();
        String cluster = globalConfiguration.getCurrentCluster();
        String asgName = globalConfiguration.getCurrentASG();

        DockerHosts hosts;
        try {
            hosts = loadHosts(cluster, asgName);
        } catch (ECSException ex) {
            //mark all futures with exception.. and let the clients wait and retry..
            while (pair != null) {
                pair.getRight().handle(ex);
                pair = requests.poll();
            }
            logger.error("Cannot query cluster " + cluster + " containers", ex);
            return;
        }
        boolean someDiscarded = false;
        while (pair != null) {
            try {
                logger.debug("Processing request for {}", request);
                Optional<DockerHost> candidate = selectHost(hosts.fresh(), request.getMemory(), request.getCpu());
                if (candidate.isPresent()) {
                    DockerHost candidateHost = candidate.get();
                    SchedulingResult schedulingResult = schedulerBackend.schedule(candidateHost.getContainerInstanceArn(), cluster, request, globalConfiguration.getTaskDefinitionName());
                    hosts.addUsedCandidate(candidateHost);
                    candidateHost.reduceAvailableCpuBy(request.getCpu());
                    candidateHost.reduceAvailableMemoryBy(request.getMemory());
                    pair.getRight().handle(schedulingResult);
                    lackingCPU = Math.max(0, lackingCPU - request.getCpu());
                    lackingMemory = Math.max(0, lackingMemory - request.getMemory());
                    // If we hit a stage where we're able to allocate a job + our deficit is less than a single agent
                    // Clear everything out, we're probably fine
                    if (lackingCPU < Constants.AGENT_CPU || lackingMemory < Constants.AGENT_MEMORY) {
                        consideredRequestIdentifiers.clear();
                        lackingCPU = 0;
                        lackingMemory = 0;
                    }
                } else {
                    // Note how much capacity we're lacking
                    // But don't double count the same request that comes through
                    if (consideredRequestIdentifiers.add(request.getIdentifier())) {
                        lackingCPU += request.getCpu();
                        lackingMemory += request.getMemory();
                    }
                    //scale up + down and set all other queued requests to null.
                    someDiscarded = true;
                    pair.getRight().handle(new ECSException("Capacity not available"));
                }
            } catch (ECSException ex) {
                logger.error("Scheduling failed", ex);
                pair.getRight().handle(ex);
            }
            pair = requests.poll();
            if (pair != null) {
                request = pair.getLeft();
            }
        }

        //see if we need to scale up or down..
        int currentSize = hosts.getUsableSize();
        int disconnectedSize = hosts.agentDisconnected().size() - terminateInstances(selectDisconnectedAgents(hosts), asgName, false);
        int desiredScaleSize = currentSize;
        //calculate usage from used fresh instances only
        if (someDiscarded || percentageUtilized(hosts.usedFresh()) >= highWatermark) {
            // cpu and memory requirements in instances
            long cpuRequirements = lackingCPU / computeInstanceCPULimits(hosts.allUsable());
            long memoryRequirements = lackingMemory / computeInstanceMemoryLimits(hosts.allUsable());
            logger.info("Scaling w.r.t. this much cpu " + lackingCPU);
            //if there are no unused fresh ones, scale up based on how many requests are pending, but always scale up
            //by at least one instance.
            long extraRequired = 1 + Math.max(cpuRequirements, memoryRequirements);

            desiredScaleSize += extraRequired;
        }
        int terminatedCount = terminateInstances(selectToTerminate(hosts), asgName, true);
        desiredScaleSize = desiredScaleSize - terminatedCount;
        //we are reducing the currentSize by the terminated list because that's
        //what the terminateInstances method should reduce it to.
        currentSize = currentSize - terminatedCount;
        try {
            Pair<Integer, Integer> p = schedulerBackend.getCurrentASGCapacity(asgName);
            // we need to scale up while ignoring any broken containers, eg.
            // if 3 instances are borked and 2 ok, we need to scale to 6 and not 3 as the desiredScaleSize is calculated 
            // up to this point.
            desiredScaleSize = desiredScaleSize + disconnectedSize;
            //never can scale beyond max capacity, will get an error then and not scale
            desiredScaleSize = Math.min(desiredScaleSize, p.getRight());
            int asgSize = p.getLeft();
            if (desiredScaleSize > currentSize && desiredScaleSize > asgSize) {
                //this is only meant to scale up!
                schedulerBackend.scaleTo(desiredScaleSize, asgName);
            }
        } catch (ECSException ex) {
            logger.error("Scaling of " + asgName + " failed", ex);
        }
    }
    
    DockerHosts loadHosts(String cluster, String asgName) throws ECSException {
        //this can take time (network) and in the meantime other requests can accumulate.
        Map<String, ContainerInstance> containerInstances = schedulerBackend.getClusterContainerInstances(cluster).stream().collect(Collectors.toMap(ContainerInstance::getEc2InstanceId, Function.identity()));
        // We need these as there is potentially a disparity between instances with container instances registered
        // in the cluster and instances which are part of the ASG. Since we detach unneeded instances from the ASG
        // then terminate them, if the cluster still reports the instance as connected we might assign a task to
        // the instance, which will soon terminate. This leads to sad builds, so we intersect the instances reported
        // from both ECS and ASG
        Set<String> asgInstances = schedulerBackend.getAsgInstanceIds(asgName);
        Set<String> allIds = new HashSet<>();
        allIds.addAll(asgInstances);
        allIds.addAll(containerInstances.keySet());
        Map<String, Instance> instances = schedulerBackend.getInstances(allIds).stream().collect(Collectors.toMap(Instance::getInstanceId, Function.identity()));
       
        Map<String, DockerHost> dockerHosts = new HashMap<>();
        containerInstances.forEach((String t, ContainerInstance u) -> {
            Instance ec2 = instances.get(t);
            if (ec2 != null) {
                try {
                    dockerHosts.put(t, new DockerHost(u, ec2));
                } catch (ECSException ex) {
                    logger.error("Skipping incomplete docker host", ex);
                }
            }
        });
        
        if (asgInstances.size() != containerInstances.size()) {
            logger.warn("Scheduler got different lengths for instances ({}) and container instances ({})", asgInstances.size(), containerInstances.size());
        }
        return new DockerHosts(dockerHosts.values(), stalePeriod);
    }
    
    private void checkScaleDown() {
        try {
            String asg = globalConfiguration.getCurrentASG();
            DockerHosts hosts = loadHosts(globalConfiguration.getCurrentCluster(), asg);
            terminateInstances(selectDisconnectedAgents(hosts), asg, false);
            terminateInstances(selectToTerminate(hosts), asg, true);
        } catch (ECSException ex) {
            logger.error("Failed to scale down", ex);
        }
    }
    

    void shutdownExecutor() {
        executor.shutdown();
    }

    @Override
    public void destroy() throws Exception {
        shutdownExecutor();
    }
    
    private List<String> selectDisconnectedAgents(DockerHosts hosts) {
        return hosts.agentDisconnected().stream()
// container without agent still shows like it's running something but it's not true, all the things are doomed.
// maybe reevaluate at some point when major ecs changes arrive.                
//                .filter(t -> t.runningNothing()) 
                .map(DockerHost::getInstanceId)
                .collect(Collectors.toList());
    }

    List<String> selectToTerminate(DockerHosts hosts) {
        List<String> toTerminate = Stream.concat(hosts.unusedStale().stream(), hosts.unusedFresh().stream())
                .map(DockerHost::getInstanceId)
                .collect(Collectors.toList());
        // If we're terminating all of our hosts (and we have any) keep one
        // around
        if (hosts.getUsableSize() == toTerminate.size() && !toTerminate.isEmpty()) {
            toTerminate.remove(0);
        }
        return toTerminate;
    }

    private int terminateInstances(List<String> toTerminate, String asgName, boolean decrementAsgSize) {
        if (!toTerminate.isEmpty()) {
            if (toTerminate.size() > 15) {
                //actual AWS limit is apparently 20
                logger.info("Too many instances to kill in one go ({}), killing the first 15 only.", toTerminate.size());
                toTerminate = toTerminate.subList(0, 14);
            }
            try {
                schedulerBackend.terminateInstances(toTerminate, asgName, decrementAsgSize);
            } catch (ECSException ex) {
                logger.error("Terminating instances failed", ex);
                return 0;
            }
        }
        return toTerminate.size();
    }

    /**
     * compute current value for available instance CPU of currently running instances
     * 
     * @param hosts known current hosts
     * @return number of CPU power available
     */
    private int computeInstanceCPULimits(Collection<DockerHost> hosts) {
        //we settle on minimum as that's the safer option here, better to scale faster than slower.
        //the alternative is to perform more checks with the asg/launchconfiguration in aws to see what
        // the current instance size is in launchconfig
        OptionalInt minCpu = hosts.stream().mapToInt((DockerHost value) -> value.getInstanceCPU()).min();
        //if no values found (we have nothing in our cluster, go with arbitrary value until something starts up.
        //current arbitrary values based on "m4.4xlarge"
        return minCpu.orElse(DockerHost.DEFAULT_INSTANCE_CPU);
    }
 
    
    /**
     * compute current value for available instance memory of currently running instances
     * 
     * @param hosts known current hosts
     * @return number of memory available
     */
    private int computeInstanceMemoryLimits(Collection<DockerHost> hosts) {
        //we settle on minimum as that's the safer option here, better to scale faster than slower.
        //the alternative is to perform more checks with the asg/launchconfiguration in aws to see what
        // the current instance size is in launchconfig
        OptionalInt minMemory = hosts.stream().mapToInt((DockerHost value) -> value.getInstanceMemory()).min();
        //if no values found (we have nothing in our cluster, go with arbitrary value until something starts up.
        //current arbitrary values based on "m4.4xlarge"
        return minMemory.orElse(DockerHost.DEFAULT_INSTANCE_MEMORY);
    }

    private class EndlessPolling implements Runnable {

        public EndlessPolling() {
        }

        @Override
        public void run() {
            try {
                Pair<SchedulingRequest, SchedulingCallback> pair = requests.poll(30, TimeUnit.MINUTES);
                if (pair != null) {
                    processRequests(pair);
                } else {
                    checkScaleDown();
                }
            } catch (InterruptedException ex) {
                logger.info("Interrupted", ex);
            } catch (RuntimeException ex) {
                logger.error("Runtime Exception", ex);
            } catch (Throwable t) {
                logger.error("A very unexpected throwable", t);
            } finally {
                //try finally to guard against unexpected exceptions.
                executor.submit(this);
            }
        }
    }
}