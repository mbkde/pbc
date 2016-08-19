package com.atlassian.buildeng.ecs.scheduling;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.annotations.VisibleForTesting;
import java.util.UUID;

public class SchedulingRequest {
    private final UUID identifier;
    private final String resultId;
    private final int revision;
    private final int cpu;
    private final int memory;
    private final Configuration configuration;

    //only here because rewriting the tests where custom CPU/memory values are
    //used to use just REGULAR/SMALL sizing is a major pain
    @Deprecated
    @VisibleForTesting
    public SchedulingRequest(UUID identifier, String resultId, int revision, int cpu, int memory, Configuration configuration) {
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.cpu = cpu;
        this.memory = memory;
        this.configuration = configuration;
    }
    
    public SchedulingRequest(UUID identifier, String resultId, int revision, Configuration configuration) {
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.configuration = configuration;
        this.cpu = configuration.getCPUTotal();
        this.memory = configuration.getMemoryTotal();
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

    public Configuration getConfiguration() {
        return configuration;
    }

}
