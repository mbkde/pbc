package com.atlassian.buildeng.ecs;

public class ContainerInstanceInfo implements Comparable<ContainerInstanceInfo> {
    private Integer remainingMemory;
    private String arn;

    public ContainerInstanceInfo(Integer remainingMemory, String arn) {
        this.remainingMemory = remainingMemory;
        this.arn = arn;
    }

    public Integer getRemainingMemory() {
        return remainingMemory;
    }

    public String getArn() {
        return arn;
    }

    public boolean canRun(Integer requestedMemory) {
        return (requestedMemory < remainingMemory);
    }

    @Override
    public int compareTo(ContainerInstanceInfo x) {
        if (remainingMemory.equals(x.remainingMemory)) {
            return arn.compareTo(x.arn);
        } else {
            return remainingMemory.compareTo(x.remainingMemory);
        }
    }
}
