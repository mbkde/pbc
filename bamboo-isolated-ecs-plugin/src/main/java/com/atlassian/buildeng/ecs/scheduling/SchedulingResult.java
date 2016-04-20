package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ecs.model.StartTaskResult;

public class SchedulingResult {
    private StartTaskResult startTaskResult;
    private String containerArn;

    public SchedulingResult(StartTaskResult startTaskResult, String containerArn) {
        this.startTaskResult = startTaskResult;
        this.containerArn = containerArn;
    }

    public StartTaskResult getStartTaskResult() {
        return startTaskResult;
    }

    public String getContainerArn() {
        return containerArn;
    }
}
