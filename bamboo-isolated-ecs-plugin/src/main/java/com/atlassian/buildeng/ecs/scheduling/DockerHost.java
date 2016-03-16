package com.atlassian.buildeng.ecs.scheduling;

import java.util.Date;

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

    public Integer getRemainingMemory() {
        return remainingMemory;
    }

    public Integer getRemainingCpu() {
        return remainingCpu;
    }

    public String getContainerInstanceArn() {
        return containerInstanceArn;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Date getLaunchTime() {
        return launchTime;
    }

    public boolean canRun(Integer requiredMemory, Integer requiredCpu) {
        return (requiredMemory <= remainingMemory) &&
                requiredCpu <= remainingCpu;
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
