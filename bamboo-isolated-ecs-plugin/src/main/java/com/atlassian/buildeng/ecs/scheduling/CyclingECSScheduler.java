package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CyclingECSScheduler implements ECSScheduler {
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One (1) week
    static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMinutes(5); // Give instances 5 minutes to pick up a task
    static final double DEFAULT_HIGH_WATERMARK = 0.9; // Scale when cluster is at 90% of maximum capacity
    static final String DEFAULT_ASG_NAME = "Staging Bamboo ECS";

    private final Duration stalePeriod;
    private final Duration gracePeriod;
    private final double highWatermark;
    private final String asgName;
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);
    
    private final SchedulerBackend schedulerBackend;

    public CyclingECSScheduler(SchedulerBackend schedulerBackend) {
        stalePeriod = DEFAULT_STALE_PERIOD;
        gracePeriod = DEFAULT_GRACE_PERIOD;
        highWatermark = DEFAULT_HIGH_WATERMARK;
        asgName = DEFAULT_ASG_NAME;
        this.schedulerBackend = schedulerBackend;
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

    static Map<Boolean, List<DockerHost>> partitionFreshness (List<DockerHost> dockerHosts, Duration stalePeriod) {
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

    private List<DockerHost> unusedFreshInstances(List<DockerHost> freshHosts, final Optional<DockerHost> candidateOpt) throws ECSException {
        DockerHost candidate = candidateOpt.isPresent() ? candidateOpt.get() : null;
        return freshHosts.stream()
                .filter(dockerHost -> !dockerHost.equals(candidate))
                .filter(DockerHost::runningNothing)
                .filter(dockerHost -> dockerHost.ageMillis() > gracePeriod.toMillis())
                .collect(Collectors.toList());
    }

    // Scale up if capacity is near full
    static public double percentageUtilized(List<DockerHost> freshHosts) {
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
        List<ContainerInstance> containerInstances = schedulerBackend.getClusterContainerInstances(cluster);
        final List<DockerHost> dockerHosts = getDockerHosts(containerInstances);
        int currentSize = dockerHosts.size();
        
        final Map<Boolean, List<DockerHost>> partitionedHosts = partitionFreshness(dockerHosts);
        final List<DockerHost> freshHosts = partitionedHosts.get(true);
        final List<DockerHost> staleHosts = partitionedHosts.get(false);
        Optional<DockerHost> candidate = selectHost(freshHosts, requiredMemory, requiredCpu);
        
        List<DockerHost> unusedStales = unusedStaleInstances(staleHosts);
        List<DockerHost> unusedFresh = unusedFreshInstances(freshHosts, candidate);
        List<DockerHost> usedFresh = new ArrayList<>(freshHosts);
        usedFresh.removeAll(unusedFresh);
        
        int desiredScaleSize = currentSize;
        
        //calculate usage from used fresh instances only
        if (!candidate.isPresent() || percentageUtilized(usedFresh) >= highWatermark) {
            //if there are no unused fresh ones, scale up
            if (unusedFresh.isEmpty()) {
                desiredScaleSize = desiredScaleSize + 1;
            } else {
                //otherwise just reuse one of the unused ones
                unusedFresh.remove(0);
            }
        } 
        String arn = candidate.isPresent() ? candidate.get().getContainerInstanceArn() : null;
        List<String> toTerminate = Stream.concat(unusedStales.stream(), unusedFresh.stream())
                .map(DockerHost::getInstanceId)
                .collect(Collectors.toList());
        desiredScaleSize = desiredScaleSize - toTerminate.size();
        if (!toTerminate.isEmpty()) {
            schedulerBackend.terminateInstances(toTerminate);
        }
        if (desiredScaleSize != currentSize) {
            schedulerBackend.scaleTo(desiredScaleSize, asgName);
        }
        return arn;
    }
}
