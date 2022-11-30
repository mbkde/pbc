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
import java.util.UUID;

public class SchedulingRequest {
    private final UUID identifier;
    private final String resultId;
    private final int revision;
    private final int cpu;
    private final int memory;
    private final Configuration configuration;
    private final long queueTimestamp;
    private final String buildKey;

    public SchedulingRequest(UUID identifier,
            String resultId,
            int revision,
            int cpu,
            int memory,
            Configuration configuration,
            long queueTimestamp,
            String buildKey) {
        this.identifier = identifier;
        this.resultId = resultId;
        this.revision = revision;
        this.configuration = configuration;
        this.cpu = cpu;
        this.memory = memory;
        this.queueTimestamp = queueTimestamp;
        this.buildKey = buildKey;
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

    public String getBuildKey() {
        return buildKey;
    }
}
