package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.atlassian.buildeng.ecs.exceptions.ECSException;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.TestOnly;

public class DockerHost {
    private int remainingMemory;
    private int remainingCpu;
    private final int registeredMemory;
    private final int registeredCpu;
    private final String containerInstanceArn;
    private final String instanceId;
    private final Date launchTime;
    private final boolean agentConnected;

    @TestOnly
    DockerHost(int remainingMemory, int remainingCpu, int registeredMemory, int registeredCpu, String containerInstanceArn, String instanceId, Date launchTime, boolean agentConnected) {
        this.remainingMemory = remainingMemory;
        this.remainingCpu = remainingCpu;
        this.registeredMemory = registeredMemory;
        this.registeredCpu = registeredCpu;
        this.containerInstanceArn = containerInstanceArn;
        this.instanceId = instanceId;
        this.launchTime = launchTime;
        this.agentConnected = agentConnected;
    }

    public DockerHost(ContainerInstance containerInstance, Instance instance) throws ECSException {
        remainingMemory  = getIntegralResource(containerInstance, true,  "MEMORY");
        remainingCpu     = getIntegralResource(containerInstance, true,  "CPU");
        registeredMemory = getIntegralResource(containerInstance, false, "MEMORY");
        registeredCpu    = getIntegralResource(containerInstance, false, "CPU");
        containerInstanceArn = containerInstance.getContainerInstanceArn();
        instanceId = containerInstance.getEc2InstanceId();
        launchTime = instance.getLaunchTime();
        agentConnected = containerInstance.isAgentConnected();
    }

    private static int getIntegralResource(ContainerInstance containerInstance, Boolean isRemaining, String name) throws ECSException {
        List<Resource> resources = isRemaining ? containerInstance.getRemainingResources() : containerInstance.getRegisteredResources();
        return resources.stream()
                .filter(resource -> resource.getName().equals(name))
                .map(Resource::getIntegerValue)
                .filter(Objects::nonNull) // Apparently Resource::getIntegerValue can be null? but we want an int only.
                .findFirst()
                .orElseThrow(() -> new ECSException(new Exception(String.format(
                        "Container Instance %s missing '%s' resource", containerInstance.getContainerInstanceArn(), name
                ))));
    }

    public boolean canRun(int requiredMemory, int requiredCpu) {
        return requiredMemory <= remainingMemory && requiredCpu <= remainingCpu;
    }

    public boolean runningNothing() {
        return registeredMemory == remainingMemory && registeredCpu == remainingCpu;
    }

    public long ageMillis() {
        return System.currentTimeMillis() - launchTime.getTime();
    }

    public boolean inSecondHalfOfBillingCycle() {
        // Mod by hour
        long millisSinceStartOfCycle = ageMillis() % (1000 * 60 * 60);
        // Are we in the second half hour of an hourly cycle
        return millisSinceStartOfCycle >= 1000 * 60 * 30;
    }

    static Comparator<DockerHost> compareByResources() {
        return (o1, o2) -> {
            if (o1.remainingMemory == o2.remainingMemory) {
                return Integer.compare(o1.remainingCpu, o2.remainingCpu);
            } else {
                return Integer.compare(o1.remainingMemory, o2.remainingMemory);
            }
        };
    }

    public int getRemainingMemory() {
        return remainingMemory;
    }

    public int getRemainingCpu() {
        return remainingCpu;
    }
    
    public void reduceAvailableCpuBy(int cpu) {
        remainingCpu = remainingCpu - cpu;
    }
    
    public void reduceAvailableMemoryBy( int memory ) {
        remainingMemory = remainingMemory - memory;
    }

    public int getRegisteredCpu() {
        return registeredCpu;
    }

    public String getContainerInstanceArn() {
        return containerInstanceArn;
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
