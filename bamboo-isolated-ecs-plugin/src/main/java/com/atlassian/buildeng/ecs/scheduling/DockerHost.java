package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.atlassian.buildeng.ecs.exceptions.ECSException;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class DockerHost {
    private Integer remainingMemory;
    private Integer remainingCpu;
    private Integer registeredMemory;
    private Integer registeredCpu;
    private String containerInstanceArn;
    private String instanceId;
    private Date launchTime;
    private Boolean agentConnected;

    public DockerHost(Integer remainingMemory, Integer remainingCpu, Integer registeredMemory, Integer registeredCpu, String containerInstanceArn, String instanceId, Date launchTime, Boolean agentConnected) {
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

    private static Integer getIntegralResource(ContainerInstance containerInstance, Boolean isRemaining, String name) throws ECSException {
        List<Resource> resources = isRemaining ? containerInstance.getRemainingResources() : containerInstance.getRegisteredResources();
        return resources.stream()
                .filter(resource -> resource.getName().equals(name))
                .map(Resource::getIntegerValue)
                .findFirst()
                .orElseThrow(() -> new ECSException(new Exception(String.format(
                        "Container Instance %s missing '%s' resource", containerInstance.getContainerInstanceArn(), name
                ))));
    }

    public boolean canRun(Integer requiredMemory, Integer requiredCpu) {
        return (requiredMemory <= remainingMemory && requiredCpu <= remainingCpu);
    }

    public boolean runningNothing() {
        return (registeredMemory.equals(remainingMemory) && registeredCpu.equals(remainingCpu));
    }

    public long ageMillis() {
        return System.currentTimeMillis() - launchTime.getTime();
    }

    static Comparator<DockerHost> compareByResources() {
        return (o1, o2) -> {
            if (o1.remainingMemory.equals(o2.remainingMemory)) {
                return o1.remainingCpu.compareTo(o2.remainingCpu);
            } else {
                return o1.remainingMemory.compareTo(o2.remainingMemory);
            }
        };
    }

    public Integer getRemainingMemory() {
        return remainingMemory;
    }

    public Integer getRemainingCpu() {
        return remainingCpu;
    }

    public Integer getRegisteredCpu() {
        return registeredCpu;
    }

    public String getContainerInstanceArn() {
        return containerInstanceArn;
    }

    public Boolean getAgentConnected() {
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

        if (remainingMemory != null ? !remainingMemory.equals(that.remainingMemory) : that.remainingMemory != null)
            return false;
        if (remainingCpu != null ? !remainingCpu.equals(that.remainingCpu) : that.remainingCpu != null) return false;
        if (registeredMemory != null ? !registeredMemory.equals(that.registeredMemory) : that.registeredMemory != null)
            return false;
        if (registeredCpu != null ? !registeredCpu.equals(that.registeredCpu) : that.registeredCpu != null)
            return false;
        if (containerInstanceArn != null ? !containerInstanceArn.equals(that.containerInstanceArn) : that.containerInstanceArn != null)
            return false;
        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) return false;
        if (launchTime != null ? !launchTime.equals(that.launchTime) : that.launchTime != null) return false;
        return !(agentConnected != null ? !agentConnected.equals(that.agentConnected) : that.agentConnected != null);

    }

    @Override
    public int hashCode() {
        int result = remainingMemory != null ? remainingMemory.hashCode() : 0;
        result = 31 * result + (remainingCpu != null ? remainingCpu.hashCode() : 0);
        result = 31 * result + (registeredMemory != null ? registeredMemory.hashCode() : 0);
        result = 31 * result + (registeredCpu != null ? registeredCpu.hashCode() : 0);
        result = 31 * result + (containerInstanceArn != null ? containerInstanceArn.hashCode() : 0);
        result = 31 * result + (instanceId != null ? instanceId.hashCode() : 0);
        result = 31 * result + (launchTime != null ? launchTime.hashCode() : 0);
        result = 31 * result + (agentConnected != null ? agentConnected.hashCode() : 0);
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
