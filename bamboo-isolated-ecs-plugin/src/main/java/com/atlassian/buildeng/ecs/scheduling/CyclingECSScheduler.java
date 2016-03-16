package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.atlassian.fugue.Either;
import com.sun.research.ws.wadl.Doc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class CyclingECSScheduler implements ECSScheduler {
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One week

    private Duration stalePeriod;
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);

    public CyclingECSScheduler() {
        this.stalePeriod = DEFAULT_STALE_PERIOD;
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

    // Get the instance models of the given instance ARNs
    private List<DockerHost> getDockerHosts(List<ContainerInstance> containerInstances) {
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
        instances.sort((o1, o2) -> o1.getInstanceId().compareTo(o2.getInstanceId()));
        containerInstances.sort((o1, o2) -> o1.getEc2InstanceId().compareTo(o2.getEc2InstanceId()));

        int iSize = instances.size();
        int ciSize = containerInstances.size();

        List<DockerHost> dockerHosts = new ArrayList<>();

        if (iSize != ciSize){
            logger.warn(String.format("Scheduler got different lengths for instances (%d) and container instances (%d)", iSize, ciSize));
        } else {
            for (int i = 0; i < ciSize; i++) {
                ContainerInstance ci = containerInstances.get(i);
                Integer remainingMemory = ci.getRemainingResources().stream().filter(resource -> resource.getName().equals("MEMORY")).map(Resource::getIntegerValue).collect(Collectors.toList()).get(0);
                Integer remainingCpu = ci.getRemainingResources().stream().filter(resource -> resource.getName().equals("CPU")).map(Resource::getIntegerValue).collect(Collectors.toList()).get(0);
                String containerInstanceArn = ci.getContainerInstanceArn();
                String instanceId = ci.getEc2InstanceId();
                Date launchTime = instances.get(i).getLaunchTime();
                dockerHosts.add(new DockerHost(remainingMemory, remainingCpu, containerInstanceArn, instanceId, launchTime));
            }
        }
        return dockerHosts;
    }

    @Override
    public String schedule(String cluster, Integer requiredMemory, Integer requiredCpu) {
        AmazonECSClient ecsClient = new AmazonECSClient();
        AmazonEC2Client ec2Client = new AmazonEC2Client();
        boolean finished = false;
        Collection<String> containerInstanceArns = getClusterContainerInstanceArns(cluster);
        DescribeContainerInstancesRequest req = new DescribeContainerInstancesRequest()
                .withCluster(cluster)
                .withContainerInstances(containerInstanceArns);
        Collection<DockerHost> dockerHosts = getDockerHosts(ecsClient.describeContainerInstances(req).getContainerInstances());
        Optional<DockerHost> candidate = dockerHosts.stream().filter(dockerHost -> new Date().getTime() - dockerHost.getLaunchTime().getTime() < stalePeriod.toMillis() &&
                dockerHost.canRun(requiredMemory, requiredCpu)).sorted().findFirst();
        String arn = null;
        if (candidate.isPresent()) {
            arn = candidate.get().getContainerInstanceArn();
        }
        return arn;
    }
}
