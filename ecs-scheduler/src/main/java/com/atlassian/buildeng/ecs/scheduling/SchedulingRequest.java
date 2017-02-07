/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    private long queueTimestamp = -1;

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
    
    public SchedulingRequest(UUID identifier, String resultId, int revision, Configuration configuration, long queueTimestamp) {
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.configuration = configuration;
        this.cpu = configuration.getCPUTotal();
        this.memory = configuration.getMemoryTotal();
        this.queueTimestamp = queueTimestamp;
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

    public long getQueueTimeStamp() {
        return queueTimestamp;
    }

}
