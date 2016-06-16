package com.atlassian.buildeng.ecs.scheduling;

import java.util.UUID;

public class SchedulingRequest {
    private final UUID identifier;
    private final String resultId;
    private final int revision;
    private final int cpu;
    private final int memory;

    public SchedulingRequest(UUID identifier, String resultId, int revision, int cpu, int memory) {
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.cpu = cpu;
        this.memory = memory;
    }

    public UUID getIdentifier() {
        return identifier;
    }

    public String getResultId() {
        return resultId;
    }

    public int getRevision() {
        return revision;
    }

    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

}
