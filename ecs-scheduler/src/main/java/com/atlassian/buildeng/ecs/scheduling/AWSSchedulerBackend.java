/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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
import com.amazonaws.services.autoscaling.model.DetachInstancesRequest;
import com.amazonaws.services.autoscaling.model.DetachInstancesResult;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest;
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.ContainerOverride;
import com.amazonaws.services.ecs.model.DeregisterContainerInstanceRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.StartTaskRequest;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskOverride;
import com.amazonaws.services.ecs.model.UpdateContainerInstancesStateRequest;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * class encapsulating all AWS interaction for the CyclingECSScheduler
 */
public class AWSSchedulerBackend implements SchedulerBackend {
    private final static Logger logger = LoggerFactory.getLogger(AWSSchedulerBackend.class);
    private final Map<String, Instance> cachedInstances = new HashMap<>();

    //there seems to be a limit of 100 to the tasks that can be described in a batch
    private static final int MAXIMUM_TASKS_TO_DESCRIBE = 90;

    @Inject
    public AWSSchedulerBackend() {
    }
    
    @Override
    public List<ContainerInstance> getClusterContainerInstances(String cluster) throws ECSException {
        try {
            AmazonECSClient ecsClient = new AmazonECSClient();
            ListContainerInstancesRequest listReq = new ListContainerInstancesRequest()
                    .withCluster(cluster);

            // Get containerInstanceArns
            boolean finished = false;
            List<String> containerInstanceArns = new ArrayList<>();
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

            if (containerInstanceArns.isEmpty()) {
                return Collections.emptyList();
            } else {
                return Lists.partition(containerInstanceArns, 99).stream().flatMap((List<String> t) -> {
                    DescribeContainerInstancesRequest describeReq = new DescribeContainerInstancesRequest()
                            .withCluster(cluster)
                            .withContainerInstances(t);
                    return ecsClient.describeContainerInstances(describeReq).getContainerInstances().stream();
                }).collect(Collectors.toList());
            }
        } catch (Exception ex) {
            throw new ECSException(ex);
        }
    }

    @Override
    public List<Instance> getInstances(Collection<String> instanceIds) throws ECSException {
        // if not in instanceIds, remove from cache
        List<String> stale = cachedInstances.entrySet().stream()
                .map(Map.Entry::getKey)
                .filter(t -> !instanceIds.contains(t))
                .collect(Collectors.toList());

        stale.forEach(cachedInstances::remove);
        List<String> misses = instanceIds.stream().filter(t -> !cachedInstances.containsKey(t)).collect(Collectors.toList());
        if (!misses.isEmpty()) try {
            AmazonEC2Client ec2Client = new AmazonEC2Client();
            DescribeInstancesRequest req = new DescribeInstancesRequest()
                    .withInstanceIds(misses);
            boolean finished = false;

            while (!finished) {
                DescribeInstancesResult describeInstancesResult = ec2Client.describeInstances(req);
                describeInstancesResult.getReservations().stream()
                        .flatMap(t -> t.getInstances().stream())
                        .forEach(t -> cachedInstances.put(t.getInstanceId(), t));
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
        return new ArrayList<>(cachedInstances.values());
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
    public void terminateAndDetachInstances(List<DockerHost> hosts, String asgName, boolean decrementSize, String clusterName) throws ECSException {
        try {
            logger.info("Detaching and terminating unused and stale instances: {}", hosts);
            //explicit deregister first BUILDENG-12397 for details
            hosts.stream().forEach((DockerHost t) -> {
                try { 
                    // BUILDENG-12585 - when an instance with disconnected agent is attempted to be deregistered,
                    // the deregister will fail because the ecs infra remembers the state of last
                    // ecs agent report and thinks there are things running on the instance.
                    if (t.getAgentConnected()) {
                        deregisterInstance(t.getContainerInstanceArn(), clusterName);
                    }
                } catch (Exception th) {
                    logger.error("Failed deregistering ecs container instance, we survive but suspicious", th);
                }
            });
            final List<String> asgInstances = hosts.stream().filter(DockerHost::isPresentInASG).map(DockerHost::getInstanceId).collect(Collectors.toList());
            if (!asgInstances.isEmpty()) {
                AmazonAutoScalingClient asClient = new AmazonAutoScalingClient();
                DetachInstancesResult result = 
                      asClient.detachInstances(new DetachInstancesRequest()
                            .withAutoScalingGroupName(asgName)
                              //only detach instances that are actually in the ASG group
                            .withInstanceIds(asgInstances)
                            .withShouldDecrementDesiredCapacity(decrementSize));
                logger.info("Result of detachment: {}", result);
                terminateInstances(hosts.stream().map(DockerHost::getInstanceId).collect(Collectors.toList()));
            }
        } catch (ECSException e) {
            throw e;
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }


    @Override
    public void terminateInstances(List<String> instanceIds) throws ECSException {
        try {
            AmazonEC2 ec2Client = AmazonEC2ClientBuilder.defaultClient();
            try {
                TerminateInstancesResult ec2Result = ec2Client.terminateInstances(
                        new TerminateInstancesRequest(instanceIds));
                logger.info("Result of successful instance termination: {}" + ec2Result);
            } catch (AmazonEC2Exception e) {
                //attempt to limit the scope of BUILDENG-12143
                //according to docs when one instance in list is borked (not allowed to be killed), we might not be able to kill any.
                //so try one by one, logging the error. If at least one throws exception,
                // rethrow the original
                if (instanceIds.size() == 1) {
                    throw e; //don't retry for single instance failure
                } else {
                    AtomicBoolean failed = new AtomicBoolean(false);
                    instanceIds.forEach((String t) -> {
                        try {
                            TerminateInstancesResult ec2Result = ec2Client.terminateInstances(
                                    new TerminateInstancesRequest(Collections.singletonList(t)));
                        } catch (AmazonEC2Exception e1) {
                            failed.set(true);
                            logger.error("Failed instance termination:" + t, e1);
                        }
                    });
                    if (failed.get()) {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    @Override
    public void drainInstances(List<DockerHost> hosts, String clusterName) {
        AmazonECS ecsClient = AmazonECSClientBuilder.defaultClient();
        try {
            ecsClient.updateContainerInstancesState(new UpdateContainerInstancesStateRequest()
                    .withStatus(ContainerInstanceStatus.DRAINING)
                    .withCluster(clusterName)
                    .withContainerInstances(hosts.stream().map(DockerHost::getContainerInstanceArn).collect(Collectors.toList()))
            );
        } catch (AmazonECSException e) {
            logger.error("Failed to drain container instances", e);
        }
    }

    private void deregisterInstance(String containerInstanceArn, String cluster) {
        AmazonECS ecsClient = AmazonECSClientBuilder.defaultClient();
        try {
            ecsClient.deregisterContainerInstance(new DeregisterContainerInstanceRequest()
                    .withCluster(cluster)
                    .withContainerInstance(containerInstanceArn));
        } catch (RuntimeException e) {
            //failure to deregister is recoverable in our usecase
            //(it will be eventually deregistered as result ASG detachment)
            logger.error("Failed to deregister container instance " + containerInstanceArn, e);
        }
    }

    @Override
    public SchedulingResult schedule(DockerHost dockerHost, String cluster, SchedulingRequest request, String taskDefinition) throws ECSException {
        try {
            AmazonECS ecsClient = AmazonECSClientBuilder.defaultClient();
            TaskOverride overrides = new TaskOverride();
            ContainerOverride buildResultOverride = new ContainerOverride()
                .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_RESULT_ID).withValue(request.getResultId()))
                .withEnvironment(new KeyValuePair().withName(Constants.ECS_CONTAINER_INSTANCE_ARN_KEY).withValue(dockerHost.getContainerInstanceArn()))
                .withEnvironment(new KeyValuePair().withName("QUEUE_TIMESTAMP").withValue("" + request.getQueueTimeStamp()))
                .withEnvironment(new KeyValuePair().withName("SUBMIT_TIMESTAMP").withValue("" + System.currentTimeMillis()))
                .withEnvironment(new KeyValuePair().withName("RESULT_UUID").withValue(request.getIdentifier().toString()))
                .withName(Constants.AGENT_CONTAINER_NAME);
            overrides.withContainerOverrides(buildResultOverride);
            request.getConfiguration().getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
                List<String> adjustedCommands = adjustCommands(t, dockerHost);
                if (!adjustedCommands.isEmpty() || !t.getEnvVariables().isEmpty()) {
                    ContainerOverride ride = new ContainerOverride().withName(t.getName());
                    adjustedCommands.forEach((String t1) -> {
                        ride.withCommand(t1);
                    });
                    t.getEnvVariables().forEach((Configuration.EnvVariable t1) -> {
                        ride.withEnvironment(new KeyValuePair().withName(t1.getName()).withValue(t1.getValue()));
                    });
                    overrides.withContainerOverrides(ride);
                }
            });
            StartTaskResult startTaskResult = ecsClient.startTask(new StartTaskRequest()
                    .withCluster(cluster)
                    .withContainerInstances(dockerHost.getContainerInstanceArn())
                    .withTaskDefinition(taskDefinition + ":" + request.getRevision())
                    .withOverrides(overrides)
            );
            return new SchedulingResult(startTaskResult, dockerHost.getContainerInstanceArn(), dockerHost.getInstanceId());
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    /**
     * 
     * @param autoScalingGroup name
     * @return described autoscaling group
     * @throws ECSException 
     */
    @Override
    public AutoScalingGroup describeAutoScalingGroup(String autoScalingGroup) throws ECSException {
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
            return groups.get(0);
        } catch (Exception ex) {
            if (ex instanceof ECSException) {
                throw ex;
            } else {
                throw new ECSException(ex);
            }
        }
    }
    

    @Override
    public Collection<ArnStoppedState> checkStoppedTasks(String cluster, List<String> taskArns) throws ECSException {
        AmazonECS ecsClient = AmazonECSClientBuilder.defaultClient();
        try {
            final List<ArnStoppedState> toRet = new ArrayList<>();
            List<List<String>> partitioned = Lists.partition(taskArns, MAXIMUM_TASKS_TO_DESCRIBE);
            for (List<String> batch : partitioned) {
                DescribeTasksResult res = ecsClient.describeTasks(new DescribeTasksRequest().withCluster(cluster).withTasks(batch));
                res.getTasks().forEach((Task t) -> {
                    if ("STOPPED".equals(t.getLastStatus())) {
                        toRet.add(new ArnStoppedState(t.getTaskArn(), t.getContainerInstanceArn(), getError(t)));
                    }
                });
                res.getFailures().forEach((Failure t) -> {
                    //for missing items it's MISSING. do we convert to user level explanatory string?
                    toRet.add(new ArnStoppedState(t.getArn(), "unknown", t.getReason()));
                });
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

    private String getError(Task tsk) {
        StringBuilder sb = new StringBuilder();
        sb.append(tsk.getStoppedReason()).append(":");
        tsk.getContainers().stream()
                .filter((Container t) -> StringUtils.isNotBlank(t.getReason()))
                .forEach((c) -> {
                    sb.append(c.getName()).append("[").append(c.getReason()).append("],");
        });
        return sb.toString();
    }

    /**
     * adjust the list of commands if required, eg. in case of storage-driver switch for
     * docker in docker images.
     * @param t
     * @return
     */
    static List<String> adjustCommands(Configuration.ExtraContainer t, DockerHost host) {
        if (TaskDefinitionRegistrations.isDockerInDockerImage(t.getImage())) {
            List<String> cmds = new ArrayList<>(t.getCommands());
            Iterator<String> it = cmds.iterator();
            while (it.hasNext()) {
                String s = it.next().trim();
                if (s.startsWith("-s") || s.startsWith("--storage-driver") || s.startsWith("--storage-opt")) {
                    it.remove();
                    if (!s.contains("=") && it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
            }
            String driver = StringUtils.defaultIfEmpty(host.getContainerAttribute(Constants.STORAGE_DRIVER_PROPERTY), Constants.storage_driver);
            cmds.add("--storage-driver=" + driver);
            return cmds;
        }
        return t.getCommands();
    }

    @Override
    public void suspendProcess(String autoScalingGroupName, String processName) throws ECSException {
        try {
            AmazonAutoScalingClient asgClient = new AmazonAutoScalingClient();
            SuspendProcessesRequest req = new SuspendProcessesRequest()
                    .withAutoScalingGroupName(autoScalingGroupName)
                    .withScalingProcesses(processName);
            asgClient.suspendProcesses(req);
        } catch (Exception ex) {
            throw new ECSException(ex);
        }
    }
}
