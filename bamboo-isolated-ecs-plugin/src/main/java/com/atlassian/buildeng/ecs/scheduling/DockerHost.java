package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.atlassian.buildeng.ecs.exceptions.ECSException;

import java.util.Comparator;
import java.util.Date;

public class DockerHost {
    private Integer remainingMemory;
    private Integer remainingCpu;
    private String containerInstanceArn;
    private String instanceId;
    private Date launchTime;
    private Boolean agentConnected;

    public DockerHost(Integer remainingMemory, Integer remainingCpu, String containerInstanceArn, String instanceId, Date launchTime, Boolean agentConnected) {
        this.remainingMemory = remainingMemory;
        this.remainingCpu = remainingCpu;
        this.containerInstanceArn = containerInstanceArn;
        this.instanceId = instanceId;
        this.launchTime = launchTime;
        this.agentConnected = agentConnected;
    }

    public DockerHost(ContainerInstance containerInstance, Instance instance) throws ECSException {

        remainingMemory = containerInstance.getRemainingResources().stream()
                .filter(resource -> resource.getName().equals("MEMORY"))
                .map(Resource::getIntegerValue)
                .findFirst()
                .orElseThrow(() -> new ECSException(new Exception(String.format(
                        "Container Instance %s missing 'MEMORY' resource", containerInstance.getContainerInstanceArn()
                ))));

        remainingCpu = containerInstance.getRemainingResources().stream()
                .filter(resource -> resource.getName().equals("CPU"))
                .map(Resource::getIntegerValue)
                .findFirst()
                .orElseThrow(() -> new ECSException(new Exception(String.format(
                        "Container Instance %s missing 'CPU' resource", containerInstance.getContainerInstanceArn()
                ))));

        containerInstanceArn = containerInstance.getContainerInstanceArn();
        instanceId = containerInstance.getEc2InstanceId();
        launchTime = instance.getLaunchTime();
        agentConnected = containerInstance.isAgentConnected();
    }

    public String getContainerInstanceArn() {
        return containerInstanceArn;
    }

    public boolean canRun(Integer requiredMemory, Integer requiredCpu) {
        return (requiredMemory <= remainingMemory) &&
                requiredCpu <= remainingCpu;
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

    public Boolean getAgentConnected() {
        return agentConnected;
    }
}
