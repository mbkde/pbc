package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public CyclingECSScheduler() {
        stalePeriod = DEFAULT_STALE_PERIOD;
        gracePeriod = DEFAULT_GRACE_PERIOD;
        highWatermark = DEFAULT_HIGH_WATERMARK;
        asgName = DEFAULT_ASG_NAME;
    }

    // Get the arns of all owned container instances on a cluster
    static private Collection<String> getClusterContainerInstanceArns(String cluster) {
        AmazonECSClient ecsClient = new AmazonECSClient();
        ListContainerInstancesRequest req = new ListContainerInstancesRequest().withCluster(cluster);
        boolean finished = false;
        Collection<String> containerInstanceArns = new ArrayList<>();
        while (!finished) {
            ListContainerInstancesResult listContainerInstancesResult = ecsClient.listContainerInstances(req);
            containerInstanceArns.addAll(listContainerInstancesResult.getContainerInstanceArns());
            String nextToken = listContainerInstancesResult.getNextToken();
            if (nextToken == null) {
                finished = true;
            } else {
                req.setNextToken(nextToken);
            }
        }
        return containerInstanceArns;
    }

    static private List<Instance> getInstances(List<ContainerInstance> containerInstances) {
        AmazonEC2Client ec2Client = new AmazonEC2Client();
        DescribeInstancesRequest req = new DescribeInstancesRequest().withInstanceIds(
                containerInstances.stream().map(ContainerInstance::getEc2InstanceId).collect(Collectors.toList())
        );
        boolean finished = false;
        List<Instance> instances = new ArrayList<>();
        while (!finished) {
            DescribeInstancesResult describeInstancesResult = ec2Client.describeInstances(req);
            describeInstancesResult.getReservations().forEach(reservation -> instances.addAll(reservation.getInstances()));
            String nextToken = describeInstancesResult.getNextToken();
            if (nextToken == null) {
                finished = true;
            } else {
                req.setNextToken(nextToken);
            }
        }
        return instances;
    }

    // Get the instance models of the given instance ARNs
    static public List<DockerHost> getDockerHosts(List<ContainerInstance> containerInstances) throws ECSException {
        List<Instance> instances = getInstances(containerInstances);
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
    static public Optional<DockerHost> selectHost(List<DockerHost> candidates, int requiredMemory, int requiredCpu) {
        return candidates.stream()
            .filter(dockerHost -> dockerHost.canRun(requiredMemory, requiredCpu))
            .sorted(DockerHost.compareByResources())
            .findFirst();
    }

    static public Map<Boolean, List<DockerHost>> partitionFreshness (List<DockerHost> dockerHosts, Duration stalePeriod) {
        // Java pls
        return dockerHosts.stream()
                .filter(DockerHost::getAgentConnected)
                .collect(Collectors.partitioningBy(dockerHost -> dockerHost.ageMillis() < stalePeriod.toMillis()));
    }

    private Map<Boolean, List<DockerHost>> partitionFreshness (List<DockerHost> dockerHosts) {
        return partitionFreshness(dockerHosts, stalePeriod);
    }

    // Terminate any stale hosts not running any tasks
    static private void rotateStale(List<DockerHost> staleHosts) {
        AmazonEC2Client ec2Client = new AmazonEC2Client();
        List<String> toTerminate = staleHosts.stream()
                .filter(DockerHost::runningNothing)
                .map(DockerHost::getInstanceId)
                .collect(Collectors.toList());
        if (!toTerminate.isEmpty()) {
            ec2Client.terminateInstances(new TerminateInstancesRequest(toTerminate));
        }
    }

    private void terminateUnused(List<DockerHost> freshHosts, Optional<DockerHost> candidate) throws ECSException {
        int numRemaining = freshHosts.size();
        if (candidate.isPresent()) {
            freshHosts = freshHosts.stream()
                    .filter(dockerHost -> !dockerHost.equals(candidate.get()))
                    .collect(Collectors.toList());
        }
        AmazonEC2Client ec2Client = new AmazonEC2Client();
        List<String> toTerminate = freshHosts.stream()
                .filter(DockerHost::runningNothing)
                .filter(dockerHost -> dockerHost.ageMillis() > gracePeriod.toMillis())
                .map(DockerHost::getInstanceId)
                .collect(Collectors.toList());
        if (!toTerminate.isEmpty()) {
            ec2Client.terminateInstances(new TerminateInstancesRequest(toTerminate));
        }
        numRemaining -= toTerminate.size();
        scaleTo(numRemaining);
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

    private void scaleTo(int desiredCapacity) throws ECSException {
        AmazonAutoScalingClient asClient = new AmazonAutoScalingClient();
        try {
            asClient.setDesiredCapacity(new SetDesiredCapacityRequest()
                    .withDesiredCapacity(desiredCapacity)
                    .withAutoScalingGroupName(asgName));
        } catch (AmazonClientException e) {
            throw new ECSException(e);
        }
    }

    @Override
    public String schedule(String cluster, int requiredMemory, int requiredCpu) throws ECSException {
        AmazonECSClient ecsClient = new AmazonECSClient();
        Collection<String> containerInstanceArns = getClusterContainerInstanceArns(cluster);
        DescribeContainerInstancesRequest req = new DescribeContainerInstancesRequest()
                .withCluster(cluster)
                .withContainerInstances(containerInstanceArns);
        List<DockerHost> dockerHosts = getDockerHosts(ecsClient.describeContainerInstances(req).getContainerInstances());
        Map<Boolean, List<DockerHost>> partitionedHosts = partitionFreshness(dockerHosts);
        List<DockerHost> freshHosts = partitionedHosts.get(true);
        List<DockerHost> staleHosts = partitionedHosts.get(false);
        Optional<DockerHost> candidate = selectHost(freshHosts, requiredMemory, requiredCpu);
        String arn = null;
        rotateStale(staleHosts);
        terminateUnused(freshHosts, candidate);
        boolean scaled = false;
        if (percentageUtilized(freshHosts) >= highWatermark) {
            scaleTo(freshHosts.size() + 1);
            scaled = true;
        }
        // If we have no candidate we are out of capacity, we need to scale if we haven't already
        if (candidate.isPresent()) {
            arn = candidate.get().getContainerInstanceArn();
        } else if (!scaled) {
            scaleTo(freshHosts.size() + 1);
        }
        return arn;
    }
}
