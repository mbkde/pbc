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

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import java.util.List;

/**
 *
 * @author mkleint
 */
public interface SchedulerBackend {

    /**
     * Get all owned container instances on a cluster
     * 
     * @param cluster
     * @param autoScalingGroup
     * @return 
     * @throws com.atlassian.buildeng.ecs.exceptions.ECSException 
     */
    List<ContainerInstance> getClusterContainerInstances(String cluster, String autoScalingGroup) throws ECSException;
    
    /**
     * get EC2 Instances for the passed ContainerInstances
     * @param containerInstances
     * @return 
     * @throws com.atlassian.buildeng.ecs.exceptions.ECSException 
     */
    List<Instance> getInstances(List<ContainerInstance> containerInstances) throws ECSException;
    
    /**
     * scale the ASG to desired capacity
     * @param desiredCapacity
     * @param autoScalingGroup
     * @throws ECSException 
     */
    void scaleTo(int desiredCapacity, String autoScalingGroup) throws ECSException;
    
    /**
     * terminate the listed EC2 instances and reduce the size of ASG by the given amount
     * @param instanceIds 
     * @param autoScalingGroup 
     * @throws com.atlassian.buildeng.ecs.exceptions.ECSException 
     */
    void terminateInstances(List<String> instanceIds, String autoScalingGroup) throws ECSException;

    SchedulingResult schedule(String containerArn, String cluster, SchedulingRequest req) throws ECSException;
}
