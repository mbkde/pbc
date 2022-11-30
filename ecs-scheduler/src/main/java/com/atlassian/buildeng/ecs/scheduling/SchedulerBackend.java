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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import java.util.Collection;
import java.util.List;

public interface SchedulerBackend {

    /**
     * Get all owned container instances on a cluster.
     */
    List<ContainerInstance> getClusterContainerInstances(String cluster) throws ECSException;

    /**
     * get EC2 Instances for the passed ids.
     */
    List<Instance> getInstances(Collection<String> instanceIds) throws ECSException;

    /**
     * scale the ASG to desired capacity.
     */
    void scaleTo(int desiredCapacity, String autoScalingGroup) throws ECSException;

    /**
     * terminate the listed EC2 instances and reduce the size of ASG by the given amount.
     *
     * @param decrementSize should we decrease size of ASG or not? if not, new instance is started eventually.
     */
    void terminateAndDetachInstances(List<DockerHost> dockerHosts,
            String autoScalingGroup,
            boolean decrementSize,
            String ecsClusterName) throws ECSException;

    /**
     * terminate listed EC2 instances.
     */
    void terminateInstances(List<String> instanceIds) throws ECSException;

    /**
     * set the listed EC2 instances to "draining" state.
     */
    void drainInstances(List<DockerHost> hosts, String clusterName);

    SchedulingResult schedule(DockerHost dockerHost, String cluster, SchedulingRequest req, String taskDefinition)
            throws ECSException;

    /**
     * describe autoscaling group of given name.
     *
     * @return get AutoscalingGroup
     */
    AutoScalingGroup describeAutoScalingGroup(String autoScalingGroup) throws ECSException;

    /**
     * for given taskArn strings return an ArnStoppedState object for every taskArn
     * that is stopped or missing in the cluster.
     */
    Collection<ArnStoppedState> checkStoppedTasks(String cluster, List<String> taskArns) throws ECSException;

    void suspendProcess(String autoScalingGroupName, String azRebalance) throws ECSException;
}
