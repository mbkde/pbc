package com.atlassian.buildeng.ecs.scheduling;

import com.atlassian.util.concurrent.SettableFuture;

public class SchedulingRequest {
    private final String cluster;
    private final String asgName;
    private final Long identifier;
    private final String resultId;
    private final Integer revision;
    private final int cpu;
    private final int memory;
    private final SettableFuture<SchedulingResult> future;

    public SchedulingRequest(String cluster, String asgName, Long identifier, String resultId, Integer revision, int cpu, int memory) {
        this.cluster = cluster;
        this.asgName = asgName;
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.cpu = cpu;
        this.memory = memory;
        this.future = new SettableFuture<>();
    }

    public String getCluster() {
        return cluster;
    }

    public String getAsgName() {
        return asgName;
    }

    public Long getIdentifier() {
        return identifier;
    }

    public String getResultId() {
        return resultId;
    }

    public Integer getRevision() {
        return revision;
    }

    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

    public SettableFuture<SchedulingResult> getFuture() {
        return future;
    }

}
