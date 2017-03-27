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

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.atlassian.buildeng.ecs.exceptions.ECSException;

import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerHost implements Host {
    private final static Logger logger = LoggerFactory.getLogger(DockerHost.class);

    private int remainingMemory;
    private int remainingCpu;
    private final int registeredMemory;
    private final int registeredCpu;
    private final String containerInstanceArn;
    private final String instanceId;
    private final Date launchTime;
    private final boolean agentConnected;
    private boolean presentInASG = true;

    @TestOnly
    DockerHost(int remainingMemory, int remainingCpu, int registeredMemory, int registeredCpu, String containerInstanceArn, String instanceId, Date launchTime, boolean agentConnected, String instanceType) {
        this.remainingMemory = remainingMemory;
        this.remainingCpu = remainingCpu;
        this.registeredMemory = registeredMemory;
        this.registeredCpu = registeredCpu;
        this.containerInstanceArn = containerInstanceArn;
        this.instanceId = instanceId;
        this.launchTime = launchTime;
        this.agentConnected = agentConnected;
    }

    public DockerHost(ContainerInstance containerInstance, Instance instance, boolean inASG) throws ECSException {
        remainingMemory  = getIntegralResource(containerInstance, true,  "MEMORY");
        remainingCpu     = getIntegralResource(containerInstance, true,  "CPU");
        registeredMemory = getIntegralResource(containerInstance, false, "MEMORY");
        registeredCpu    = getIntegralResource(containerInstance, false, "CPU");
        containerInstanceArn = containerInstance.getContainerInstanceArn();
        instanceId = containerInstance.getEc2InstanceId();
        launchTime = instance.getLaunchTime();
        agentConnected = containerInstance.isAgentConnected();
        presentInASG = inASG;
    }

    private static int getIntegralResource(ContainerInstance containerInstance, Boolean isRemaining, String name) throws ECSException {
        List<Resource> resources = isRemaining ? containerInstance.getRemainingResources() : containerInstance.getRegisteredResources();
        return resources.stream()
                .filter(resource -> resource.getName().equals(name))
                .map(Resource::getIntegerValue)
                .filter(Objects::nonNull) // Apparently Resource::getIntegerValue can be null? but we want an int only.
                .findFirst()
                .orElseThrow(() -> new ECSException(String.format(
                        "Container Instance %s missing '%s' resource", containerInstance.getContainerInstanceArn(), name
                )));
    }

    public long ageMillis() {
        return System.currentTimeMillis() - launchTime.getTime();
    }

    public boolean reachingEndOfBillingCycle() {
        // Mod by hour
        long millisSinceStartOfCycle = ageMillis() % Duration.ofMinutes(60).toMillis();
        // Are we at the end of an hourly cycle?
        return millisSinceStartOfCycle >= Duration.ofMinutes(60 - Constants.MINUTES_BEFORE_BILLING_CYCLE).toMillis();
    }

    static Comparator<DockerHost> compareByResourcesAndAge() {
        Comparator<Host> resourceOnly = Host.compareByResources();
        return (o1, o2) -> {
            int result = resourceOnly.compare(o1, o2);
            if (result == 0) {
                    //for equals utilization we value older instances higher due to existing caches.
                    // we want it to come first
                    return o1.launchTime.compareTo(o2.launchTime);
            } else {
                return result;
            }
        };
    }

    @Override
    public int getRemainingMemory() {
        return remainingMemory;
    }

    @Override
    public int getRemainingCpu() {
        return remainingCpu;
    }
    
    public void reduceAvailableCpuBy(int cpu) {
        remainingCpu = remainingCpu - cpu;
    }
    
    public void reduceAvailableMemoryBy( int memory ) {
        remainingMemory = remainingMemory - memory;
    }

    /**
     * the total amount of cpu available for docker containers on the instance
     * @return
     */
    @Override
    public int getRegisteredCpu() {
        return registeredCpu;
    }

    /**
     * the amount of memory available for docker containers on the instance
     * @return
     */
    @Override
    public int getRegisteredMemory() {
        return registeredMemory;
    }

    public String getContainerInstanceArn() {
        return containerInstanceArn;
    }

    public boolean isPresentInASG() {
        return presentInASG;
    }
    
    public boolean getAgentConnected() {
        return agentConnected;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerHost that = (DockerHost) o;

        if (containerInstanceArn != null ? !containerInstanceArn.equals(that.containerInstanceArn) : that.containerInstanceArn != null) {
            return false;
        }
        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) {
            return false;
        }
        return !(launchTime != null ? !launchTime.equals(that.launchTime) : that.launchTime != null);
    }

    @Override
    public int hashCode() {
        int result = containerInstanceArn != null ? containerInstanceArn.hashCode() : 0;
        result = 31 * result + (instanceId != null ? instanceId.hashCode() : 0);
        result = 31 * result + (launchTime != null ? launchTime.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DockerHost{" +
                "remainingMemory=" + remainingMemory +
                ", remainingCpu=" + remainingCpu +
                ", registeredMemory=" + registeredMemory +
                ", registeredCpu=" + registeredCpu +
                ", containerInstanceArn='" + containerInstanceArn + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", launchTime=" + launchTime +
                ", agentConnected=" + agentConnected +
                '}';
    }
}
