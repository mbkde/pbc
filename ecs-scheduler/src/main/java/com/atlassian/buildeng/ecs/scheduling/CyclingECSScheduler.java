package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsDisconnectedEvent;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsDisconnectedPurgeEvent;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsStaleAsgInstanceEvent;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.event.api.EventPublisher;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

public class CyclingECSScheduler implements ECSScheduler, DisposableBean {
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One (1) week
    
    //grace period in minutes since launch
    // 5 might be too radical, but I haven't found any stats on what this the mean/average or 95percentile time for ec2 instance startup
    // a random poke at a single instance suggests something above one minute for startup on staging-bamboo.
    // but that can be significantly variable based on general state of AWS.
    // AWS Console - EC2 Launch time - December 16, 2016 at 3:44:35 PM UTC+11
    // AWS Console - ASG activity hisotry - Start 2016 December 16 15:44:36 UTC+11 -> End 2016 December 16 15:45:09 UTC+11
    // Cloud-init v. 0.7.6 finished at Fri, 16 Dec 2016 04:45:49 +0000. Datasource DataSourceEc2.  Up 50.12 seconds
    private static final int ASG_MISSING_IN_CLUSTER_GRACE_PERIOD = 5;

    private final Duration stalePeriod;
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);
    private long lackingCPU = 0;
    private long lackingMemory = 0;
    private final Set<UUID> consideredRequestIdentifiers = new HashSet<>();
    @VisibleForTesting
    final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<Pair<SchedulingRequest, SchedulingCallback>> requests = new LinkedBlockingQueue<>();
    
    //under high load there are interminent reports of agents being disconnected
    //but these recover very fast, only want to actively kill instances
    // that have disconnected agent for at least the amount given.
    static final int TIMEOUT_IN_MINUTES_TO_KILL_DISCONNECTED_AGENT = 20; //20 for us to be able to debug what's going on. (5 minutes only for datadog to report the event)
    @VisibleForTesting
    final Map<DockerHost, Date> disconnectedAgentsCache = new HashMap<>();
    
    //keep a list of asg instanceids that we reported as sad
    final List<String> reportedLonelyAsgInstances = new ArrayList<>();

    private final SchedulerBackend schedulerBackend;
    private final ECSConfiguration globalConfiguration;
    private final EventPublisher eventPublisher;

    @Inject
    public CyclingECSScheduler(SchedulerBackend schedulerBackend, ECSConfiguration globalConfiguration, EventPublisher eventPublisher) {
        stalePeriod = DEFAULT_STALE_PERIOD;
        this.schedulerBackend = schedulerBackend;
        this.globalConfiguration = globalConfiguration;
        this.eventPublisher = eventPublisher;
        executor.submit(new EndlessPolling());
    }

    // Select the best host to run a task with the given required resources out of a list of candidates
    // Is Nothing if there are no feasible hosts
    static Optional<DockerHost> selectHost(Collection<DockerHost> candidates, int requiredMemory, int requiredCpu) {
        return candidates.stream()
                .filter(dockerHost -> dockerHost.canRun(requiredMemory, requiredCpu))
                .sorted(DockerHost.compareByResourcesAndAge())
                .findFirst();
    }


    static double percentageUtilized(List<DockerHost> freshHosts) {
        double clusterRegisteredCPU = freshHosts.stream().mapToInt(DockerHost::getRegisteredCpu).sum();
        double clusterRemainingCPU = freshHosts.stream().mapToInt(DockerHost::getRemainingCpu).sum();
        return clusterRegisteredCPU == 0 ? 1 : 1 - clusterRemainingCPU / clusterRegisteredCPU;
        
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
        AutoScalingGroup asg;
        try {
            asg  = schedulerBackend.describeAutoScalingGroup(asgName);
            checkSuspendedProcesses(asg);
            hosts = loadHosts(cluster, asg);
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
                    if (lackingCPU < Configuration.ContainerSize.SMALL.cpu() || lackingMemory < Configuration.ContainerSize.SMALL.memory()) {
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
        int disconnectedSize = hosts.agentDisconnected().size() - terminateDisconnectedInstances(hosts, asgName);
        int desiredScaleSize = currentSize;
        if (someDiscarded) {
            // cpu and memory requirements in instances
            long cpuRequirements = lackingCPU / computeInstanceCPULimits(hosts.allUsable());
            long memoryRequirements = lackingMemory / computeInstanceMemoryLimits(hosts.allUsable());
            logger.info("Scaling w.r.t. this much cpu " + lackingCPU);
            //if there are no unused fresh ones, scale up based on how many requests are pending, but always scale up
            //by at least one instance.
            long extraRequired = Math.max(1, Math.max(cpuRequirements, memoryRequirements));

            desiredScaleSize += extraRequired;
        }
        int terminatedCount = terminateInstances(selectToTerminate(hosts), asgName, true);
        desiredScaleSize = desiredScaleSize - terminatedCount;
        //we are reducing the currentSize by the terminated list because that's
        //what the terminateInstances method should reduce it to.
        currentSize = currentSize - terminatedCount;
        try {
            // we need to scale up while ignoring any broken containers, eg.
            // if 3 instances are borked and 2 ok, we need to scale to 6 and not 3 as the desiredScaleSize is calculated 
            // up to this point.
            desiredScaleSize = desiredScaleSize + disconnectedSize;
            //never can scale beyond max capacity, will get an error then and not scale
            desiredScaleSize = Math.min(desiredScaleSize, asg.getMaxSize());
            if (desiredScaleSize > currentSize && desiredScaleSize > asg.getDesiredCapacity()) {
                //this is only meant to scale up!
                schedulerBackend.scaleTo(desiredScaleSize, asgName);
            }
        } catch (ECSException ex) {
            logger.error("Scaling of " + asgName + " failed", ex);
        }
    }
    
    DockerHosts loadHosts(String cluster, AutoScalingGroup asg) throws ECSException {
        //this can take time (network) and in the meantime other requests can accumulate.
        Map<String, ContainerInstance> containerInstances = schedulerBackend.getClusterContainerInstances(cluster).stream().collect(Collectors.toMap(ContainerInstance::getEc2InstanceId, Function.identity()));
        // We need these as there is potentially a disparity between instances with container instances registered
        // in the cluster and instances which are part of the ASG. Since we detach unneeded instances from the ASG
        // then terminate them, if the cluster still reports the instance as connected we might assign a task to
        // the instance, which will soon terminate. This leads to sad builds, so we intersect the instances reported
        // from both ECS and ASG
        Set<String> asgInstances = asg.getInstances().stream().map(x -> x.getInstanceId()).collect(Collectors.toSet());
        Set<String> allIds = new HashSet<>();
        allIds.addAll(asgInstances);
        allIds.addAll(containerInstances.keySet());
        Map<String, Instance> instances = schedulerBackend.getInstances(allIds).stream().collect(Collectors.toMap(Instance::getInstanceId, Function.identity()));
       
        Map<String, DockerHost> dockerHosts = new HashMap<>();
        containerInstances.forEach((String t, ContainerInstance u) -> {
            Instance ec2 = instances.get(t);
            if (ec2 != null) {
                try {
                    dockerHosts.put(t, new DockerHost(u, ec2, asgInstances.contains(t)));
                } catch (ECSException ex) {
                    logger.error("Skipping incomplete docker host", ex);
                }
            }
        });
        //sometimes asg instances get stuck on startup and never reach ecs, report on it.
        Set<String> lonelyAsgInstances = new HashSet<>(asgInstances);
        lonelyAsgInstances.removeAll(containerInstances.keySet());
        if (!lonelyAsgInstances.isEmpty()) {
            lonelyAsgInstances.stream()
                    .filter((String t) -> {
                        Instance ec2 = instances.get(t);
                        if (ec2 != null) {
                            return Duration.ofMinutes(ASG_MISSING_IN_CLUSTER_GRACE_PERIOD).toMillis() < (new Date().getTime() - ec2.getLaunchTime().getTime());
                        }
                        return false;
                    })
                    .filter((String t) -> !reportedLonelyAsgInstances.contains(t))
                    .forEach((String t) -> {
                        eventPublisher.publish(new DockerAgentEcsStaleAsgInstanceEvent(t));
                        reportedLonelyAsgInstances.add(t);
                        if (reportedLonelyAsgInstances.size() > 50) { //random number to keep the list from growing indefinitely
                            reportedLonelyAsgInstances.remove(0);
                        }
                        try {
                            schedulerBackend.terminateInstances(Collections.<String>singletonList(t));
                        } catch (ECSException e) {
                            logger.warn("Failed to terminate instance " + t, e);
                        }
                    });
            logger.warn("Scheduler got different lengths for instances ({}) and container instances ({})", asgInstances.size(), containerInstances.size());
        }
        return new DockerHosts(dockerHosts.values(), stalePeriod);
    }

    private void checkScaleDown() {
        try {
            String asgName = globalConfiguration.getCurrentASG();
            AutoScalingGroup asg = schedulerBackend.describeAutoScalingGroup(asgName);
            DockerHosts hosts = loadHosts(globalConfiguration.getCurrentCluster(), asg);
            terminateDisconnectedInstances(hosts, asgName);
            terminateInstances(selectToTerminate(hosts), asgName, true);
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
    
    private List<DockerHost> selectDisconnectedToKill(DockerHosts hosts, Map<DockerHost, Date> dates) {
        return hosts.agentDisconnected().stream()
// container without agent still shows like it's running something but it's not true, all the things are doomed.
// maybe reevaluate at some point when major ecs changes arrive.                
//                .filter(t -> t.runningNothing()) 
                .filter((DockerHost t) -> {
                    Date date = dates.get(t);
                    return date != null && (Duration.ofMillis(new Date().getTime() - date.getTime()).toMinutes() >= TIMEOUT_IN_MINUTES_TO_KILL_DISCONNECTED_AGENT);
                })
                .collect(Collectors.toList());
    }

    List<DockerHost> selectToTerminate(DockerHosts hosts) {
        List<DockerHost> toTerminate = Stream.concat(hosts.unusedStale().stream(), hosts.unusedFresh().stream())
                .collect(Collectors.toList());
        // If we're terminating all of our hosts (and we have any) keep one
        // around
        if (hosts.getUsableSize() == toTerminate.size() && !toTerminate.isEmpty()) {
            toTerminate.remove(0);
        }
        return toTerminate;
    }

    //the return value has 2 possible meanings.
    // 1. how many instances we actually killed
    // 2. by how much the ASG size decreaesed
    // the current code is using it in both meanings, the asg drop is not calculated now and in case
    // of errors the return value is a lie as well.
    private int terminateInstances(List<DockerHost> toTerminate, String asgName, boolean decrementAsgSize) {
        if (!toTerminate.isEmpty()) {
            if (toTerminate.size() > 15) {
                //actual AWS limit is apparently 20
                logger.info("Too many instances to kill in one go ({}), killing the first 15 only.", toTerminate.size());
                toTerminate = toTerminate.subList(0, 14);
            }
            try {
                schedulerBackend.terminateAndDetachInstances(toTerminate, asgName, decrementAsgSize);
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
        return minCpu.orElse(ECSInstance.DEFAULT_INSTANCE.getCpu());
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
        return minMemory.orElse(ECSInstance.DEFAULT_INSTANCE.getMemory());
    }

    /**
     *   update the cache with times of first report for disconnected agent. Remove those that recovered
     * , add new incidents.
     */
    private Map<DockerHost, Date> updateDisconnectedCache(Map<DockerHost, Date> cache, DockerHosts hosts) {
        cache.keySet().retainAll(hosts.agentDisconnected());
        hosts.agentDisconnected().forEach((DockerHost t) -> {
            Date date = cache.get(t);
            if (date == null) {
                cache.put(t, new Date());
            }
        });
        return cache;
    }

    //AZRebalance kills running agents, we need to suspend it.
    //not possible to do via terraform now, let's do explicitly from the plugin.
    private void checkSuspendedProcesses(AutoScalingGroup asg) throws ECSException {
        if (asg.getSuspendedProcesses() == null ||
            !asg.getSuspendedProcesses().stream()
                    .map((SuspendedProcess t) -> t.getProcessName())
                    //it's a pity aws lib doesn't have these as constants or enums
                    .filter((String t) -> "AZRebalance".equals(t))
                    .findAny().isPresent())
        {
            schedulerBackend.suspendProcess(asg.getAutoScalingGroupName(), "AZRebalance");
        }
    }

    private int terminateDisconnectedInstances(DockerHosts hosts, String asgName) {
        int oldSize = disconnectedAgentsCache.size();
        final Map<DockerHost, Date> cache = updateDisconnectedCache(disconnectedAgentsCache, hosts);
        if (!cache.isEmpty()) {
            //debugging block
            logger.warn("Hosts with disconnected agent:" + cache.size() + " " + cache.toString());
            if (oldSize != cache.size()) {
                eventPublisher.publish(new DockerAgentEcsDisconnectedEvent(cache.keySet()));
            }
        }
        final List<DockerHost> selectedToKill = selectDisconnectedToKill(hosts, cache);

        if (!selectedToKill.isEmpty()) {
            //debugging block
            logger.warn("Hosts to kill with disconnected agent:" + selectedToKill.size() + " " + selectedToKill.toString());
            //TODO it's very hard to figure out what tasks were running on the instance.
            // 1. you need to get a list of task arns for (single) instance (aws api call per instance or paged per cluster)
            // 2. describe them (another aws api call)
            // 3. in the task fine container override for bamboo-agent container and in there find the
            //    environment variable value for RESULT_ID
            eventPublisher.publish(new DockerAgentEcsDisconnectedPurgeEvent(selectedToKill));
        }
        return terminateInstances(selectedToKill, asgName, false);
    }

    private class EndlessPolling implements Runnable {

        public EndlessPolling() {
        }

        @Override
        public void run() {
            try {
                Pair<SchedulingRequest, SchedulingCallback> pair = requests.poll(Constants.POLLING_INTERVAL, TimeUnit.MINUTES);
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