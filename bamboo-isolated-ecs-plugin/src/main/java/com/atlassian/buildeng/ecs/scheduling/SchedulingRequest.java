package com.atlassian.buildeng.ecs.scheduling;

import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;

import java.util.UUID;

public class SchedulingRequest {
    private final String cluster;
    private final String asgName;
    private final UUID identifier;
    private final String resultId;
    private final Integer revision;
    private final int cpu;
    private final int memory;
    private final Callback callback;

    public SchedulingRequest(String cluster, String asgName, UUID identifier, String resultId, Integer revision, int cpu, int memory, Callback callback) {
        this.cluster = cluster;
        this.asgName = asgName;
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.cpu = cpu;
        this.memory = memory;
        this.callback = callback;
    }

    public String getCluster() {
        return cluster;
    }

    public String getAsgName() {
        return asgName;
    }

    public UUID getIdentifier() {
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

    public Callback getCallback() {
        return callback;
    }

    public static interface Callback {
        
        void handle (SchedulingResult result);
        void handle (ECSException exception);
    }
}
