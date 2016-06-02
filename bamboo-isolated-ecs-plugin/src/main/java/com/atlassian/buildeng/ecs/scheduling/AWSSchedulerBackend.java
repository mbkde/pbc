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
import com.amazonaws.services.ecs.model.ContainerOverride;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.StartTaskRequest;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskOverride;
import com.atlassian.buildeng.ecs.Constants;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * class encapsulating all AWS interaction for the CyclingECSScheduler
 */
public class AWSSchedulerBackend implements SchedulerBackend {
    private final static Logger logger = LoggerFactory.getLogger(AWSSchedulerBackend.class);

    @Override
    public List<ContainerInstance> getClusterContainerInstances(String cluster, String autoScalingGroup) throws ECSException {
        try {
            AmazonECSClient ecsClient = new AmazonECSClient();
            AmazonAutoScalingClient asgClient = new AmazonAutoScalingClient();
            ListContainerInstancesRequest listReq = new ListContainerInstancesRequest()
                    .withCluster(cluster);
            DescribeAutoScalingGroupsRequest asgReq = new DescribeAutoScalingGroupsRequest()
                    .withAutoScalingGroupNames(autoScalingGroup);

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
        } catch (Exception ex) {
            throw new ECSException(ex);
        }
    }

    @Override
    public List<Instance> getInstances(List<ContainerInstance> containerInstances) throws ECSException {
        List<Instance> instances = new ArrayList<>();
        if (!containerInstances.isEmpty()) try {
            AmazonEC2Client ec2Client = new AmazonEC2Client();
            DescribeInstancesRequest req = new DescribeInstancesRequest()
                    .withInstanceIds(containerInstances.stream().map(ContainerInstance::getEc2InstanceId).collect(Collectors.toList()));
            boolean finished = false;

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
        } catch (Exception ex) {
            throw new ECSException(ex);
        }
        return instances.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public void scaleTo(int desiredCapacity, String autoScalingGroup) throws ECSException {
        logger.info("Scaling to capacity: {} in ASG: {}", desiredCapacity, autoScalingGroup);
        try {
            AmazonAutoScalingClient asClient = new AmazonAutoScalingClient();
            asClient.setDesiredCapacity(new SetDesiredCapacityRequest()
                    .withDesiredCapacity(desiredCapacity)
                    .withAutoScalingGroupName(autoScalingGroup)
            );
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    @Override
    public void terminateInstances(List<String> instanceIds, String asgName) throws ECSException {
        try {
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
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    @Override
    public SchedulingResult schedule(String containerArn, String cluster, SchedulingRequest request) throws ECSException {
        try {
            AmazonECSClient ecsClient = new AmazonECSClient();
            ContainerOverride buildResultOverride = new ContainerOverride()
                .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_RESULT_ID).withValue(request.getResultId()))
                .withEnvironment(new KeyValuePair().withName(Constants.ECS_CONTAINER_INSTANCE_ARN_KEY).withValue(containerArn))
                .withName(Constants.AGENT_CONTAINER_NAME);
            StartTaskResult startTaskResult = ecsClient.startTask(new StartTaskRequest()
                    .withCluster(cluster)
                    .withContainerInstances(containerArn)
                    .withTaskDefinition(Constants.TASK_DEFINITION_NAME + ":" + request.getRevision())
                    .withOverrides(new TaskOverride().withContainerOverrides(buildResultOverride))
            );
            return new SchedulingResult(startTaskResult, containerArn);
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    @Override
    public int getCurrentASGDesiredCapacity(String autoScalingGroup) throws ECSException {
        try {
            AmazonAutoScalingClient asgClient = new AmazonAutoScalingClient();
            DescribeAutoScalingGroupsRequest asgReq = new DescribeAutoScalingGroupsRequest()
                    .withAutoScalingGroupNames(autoScalingGroup);
            List<AutoScalingGroup> groups = asgClient.describeAutoScalingGroups(asgReq).getAutoScalingGroups();
            if (groups.size() > 1) {
                throw new ECSException("More than one group by name:" + autoScalingGroup);
            }
            if (groups.isEmpty()) {
                throw new ECSException("No auto scaling group with name:" + autoScalingGroup);
            }
            return groups.get(0).getDesiredCapacity();
        } catch (Exception ex) {
            if (ex instanceof ECSException) {
                throw ex;
            } else {
                throw new ECSException(ex);
            }
        }
    }
    

    @Override
    public Collection<Task> checkTasks(String cluster, Collection<String> taskArns) throws ECSException {
        AmazonECSClient ecsClient = new AmazonECSClient();
        try {
            final List<Task> toRet = new ArrayList<>();
            DescribeTasksResult res = ecsClient.describeTasks(new DescribeTasksRequest().withCluster(cluster).withTasks(taskArns));
            res.getTasks().forEach((Task t) -> {
                toRet.add(t);
            });
            if (!res.getFailures().isEmpty()) {
                if (toRet.isEmpty()) {
                    throw new ECSException(Arrays.toString(res.getFailures().toArray()));
                } else {
                    logger.info("Error on retrieving tasks: {}",Arrays.toString(res.getFailures().toArray()));
                }
            }
            return toRet;
        } catch (Exception ex) {
            if (ex instanceof ECSException) {
                throw ex;
            } else {
                throw new ECSException(ex);
            }
        }
        
    }
}
