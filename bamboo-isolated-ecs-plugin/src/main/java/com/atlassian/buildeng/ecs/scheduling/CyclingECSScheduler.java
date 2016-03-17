package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
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
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One week

    private final Duration stalePeriod;
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);

    public CyclingECSScheduler() {
        stalePeriod = DEFAULT_STALE_PERIOD;
    }

    // Get the arns of all owned container instances on a cluster
    private Collection<String> getClusterContainerInstanceArns(String cluster) {
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

    private List<Instance> getInstances(List<ContainerInstance> containerInstances) {
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
    private List<DockerHost> getDockerHosts(List<ContainerInstance> containerInstances) throws ECSException {
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

    private Optional<DockerHost> selectHost(List<DockerHost> dockerHosts, Integer requiredMemory, Integer requiredCpu) {
        // Java pls
        Map<Boolean, List<DockerHost>> partitionedHosts = dockerHosts.stream().collect(
                Collectors.partitioningBy(dockerHost -> dockerHost.ageMillis() < stalePeriod.toMillis()));
        List<DockerHost> fresh = partitionedHosts.get(true);
        // TODO: Add rotation for stale hosts
        List<DockerHost> stale = partitionedHosts.get(false);
        return fresh.stream()
                .filter(dockerHost -> dockerHost.canRun(requiredMemory, requiredCpu))
                .sorted(DockerHost.compareByResources())
                .findFirst();
    }

    @Override
    public String schedule(String cluster, Integer requiredMemory, Integer requiredCpu) throws ECSException {
        AmazonECSClient ecsClient = new AmazonECSClient();
        Collection<String> containerInstanceArns = getClusterContainerInstanceArns(cluster);
        DescribeContainerInstancesRequest req = new DescribeContainerInstancesRequest()
                .withCluster(cluster)
                .withContainerInstances(containerInstanceArns);
        List<DockerHost> dockerHosts = getDockerHosts(ecsClient.describeContainerInstances(req).getContainerInstances());
        Optional<DockerHost> candidate = selectHost(dockerHosts, requiredMemory, requiredCpu);
        String arn = null;
        if (candidate.isPresent()) {
            arn = candidate.get().getContainerInstanceArn();
        }
        return arn;
    }
}
