package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;

import java.util.Date;
import java.util.stream.Collectors;

public class DockerHost implements Comparable<DockerHost> {
    private Integer remainingMemory;
    private Integer remainingCpu;
    private String containerInstanceArn;
    private String instanceId;
    private Date launchTime;

    public DockerHost(Integer remainingMemory, Integer remainingCpu, String containerInstanceArn, String instanceId, Date launchTime) {
        this.remainingMemory = remainingMemory;
        this.remainingCpu = remainingCpu;
        this.containerInstanceArn = containerInstanceArn;
        this.instanceId = instanceId;
        this.launchTime = launchTime;
    }

    public DockerHost(ContainerInstance containerInstance, Instance instance) {
        remainingMemory = containerInstance.getRemainingResources().stream().filter(resource -> resource.getName().equals("MEMORY")).map(Resource::getIntegerValue).collect(Collectors.toList()).get(0);
        remainingCpu = containerInstance.getRemainingResources().stream().filter(resource -> resource.getName().equals("CPU")).map(Resource::getIntegerValue).collect(Collectors.toList()).get(0);
        containerInstanceArn = containerInstance.getContainerInstanceArn();
        instanceId = containerInstance.getEc2InstanceId();
        launchTime = instance.getLaunchTime();
    }

    public String getContainerInstanceArn() {
        return containerInstanceArn;
    }

    public boolean canRun(Integer requiredMemory, Integer requiredCpu) {
        return (requiredMemory <= remainingMemory) &&
                requiredCpu <= remainingCpu;
    }

    public long ageMillis() {
        return new Date().getTime() - launchTime.getTime();
    }

    @Override
    public int compareTo(DockerHost x) {
        if (remainingMemory.equals(x.remainingMemory)) {
            return containerInstanceArn.compareTo(x.containerInstanceArn);
        } else {
            return remainingMemory.compareTo(x.remainingMemory);
        }
    }
}
