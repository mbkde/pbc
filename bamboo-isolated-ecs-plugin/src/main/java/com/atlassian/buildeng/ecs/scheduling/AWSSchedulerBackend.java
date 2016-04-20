/*
 * Copyright 2016 Atlassian.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DetachInstancesRequest;
import com.amazonaws.services.autoscaling.model.DetachInstancesResult;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * class encapsulating all AWS interaction for the CyclingECSScheduler
 */
public class AWSSchedulerBackend implements SchedulerBackend {
    private final static Logger logger = LoggerFactory.getLogger(AWSSchedulerBackend.class);

    @Override
    public List<ContainerInstance> getClusterContainerInstances(String cluster, String autoScalingGroup) {
        AmazonECSClient ecsClient = new AmazonECSClient();
        AmazonAutoScalingClient asgClient = new AmazonAutoScalingClient();
        ListContainerInstancesRequest listReq = new ListContainerInstancesRequest().withCluster(cluster);
        DescribeAutoScalingGroupsRequest asgReq = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroup);

        // Get containerInstanceArns
        boolean finished = false;
        Collection<String> containerInstanceArns = new ArrayList<>();
        while (!finished) {
            ListContainerInstancesResult listContainerInstancesResult = ecsClient.listContainerInstances(listReq);
            containerInstanceArns.addAll(listContainerInstancesResult.getContainerInstanceArns());
            String nextToken = listContainerInstancesResult.getNextToken();
            if (nextToken == null) {
                finished = true;
            } else {
                listReq.setNextToken(nextToken);
            }
        }

        // Get asg instances
        // We need these as there is potentially a disparity between instances with container instances registered
        // in the cluster and instances which are part of the ASG. Since we detach unneeded instances from the ASG
        // then terminate them, if the cluster still reports the instance as connected we might assign a task to
        // the instance, which will soon terminate. This leads to sad builds, so we intersect the instances reported
        // from both ECS and ASG
        Set<String> instanceIds = new HashSet<>();
        finished = false;
        while(!finished) {
            DescribeAutoScalingGroupsResult asgResult = asgClient.describeAutoScalingGroups(asgReq);
            List<AutoScalingGroup> asgs = asgResult.getAutoScalingGroups();
            instanceIds.addAll(asgs.stream()
                    .flatMap(asg -> asg.getInstances().stream().map(x -> x.getInstanceId()))
                    .collect(Collectors.toList()));
            String nextToken = asgResult.getNextToken();
            if (nextToken == null) {
                finished = true;
            } else {
                asgReq.setNextToken(nextToken);
            }
        }

        if (containerInstanceArns.isEmpty()) {
            return Collections.emptyList();
        } else {
            DescribeContainerInstancesRequest describeReq = new DescribeContainerInstancesRequest()
                    .withCluster(cluster)
                    .withContainerInstances(containerInstanceArns);
            return ecsClient.describeContainerInstances(describeReq).getContainerInstances().stream()
                    .filter(x -> instanceIds.contains(x.getEc2InstanceId()))
                    .filter(ContainerInstance::isAgentConnected)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<Instance> getInstances(List<ContainerInstance> containerInstances) {
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

    @Override
    public void scaleTo(int desiredCapacity, String autoScalingGroup) throws ECSException {
        logger.info("Scaling to capacity: {} in ASG: {}", desiredCapacity, autoScalingGroup);
        AmazonAutoScalingClient asClient = new AmazonAutoScalingClient();
        try {
            asClient.setDesiredCapacity(new SetDesiredCapacityRequest()
                    .withDesiredCapacity(desiredCapacity)
                    .withAutoScalingGroupName(autoScalingGroup));
        } catch (AmazonClientException e) {
            throw new ECSException(e);
        }
    }

    @Override
    public void terminateInstances(List<String> instanceIds, String asgName) {
        AmazonAutoScalingClient asClient = new AmazonAutoScalingClient();
        logger.info("Detaching and terminating unused and stale instances: {}", instanceIds);
        DetachInstancesResult result = 
              asClient.detachInstances(new DetachInstancesRequest()
                    .withAutoScalingGroupName(asgName)
                    .withInstanceIds(instanceIds)
                    .withShouldDecrementDesiredCapacity(true));
        logger.info("Result of detachment: {}", result);
        AmazonEC2Client ec2Client = new AmazonEC2Client();
        TerminateInstancesResult ec2Result = ec2Client.terminateInstances(new TerminateInstancesRequest(instanceIds));
        logger.info("Result of instance termination: {}" + ec2Result);
    }

    
}
