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
        return (registeredMemory == remainingMemory && registeredCpu == remainingCpu);
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
}
