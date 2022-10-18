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

package com.atlassian.buildeng.spi.isolated.docker;

import java.util.UUID;

public final class IsolatedDockerAgentRequest {

    private final Configuration configuration;
    private final String resultKey;
    private final UUID uniqueIdentifier;
    private final long queueTimestamp;
    private final String buildKey;
    private final int retryCount;
    private final boolean isPlan;
    private final String securityToken;

    /**
     * @param configuration plan configuration.
     * @param resultKey        - bamboo build result key
     * @param uniqueIdentifier - something to uniquely identifier the request with
     * @param originalQueingTimestamp - timestamp of when the job was originally queued in bamboo.
     * @param isPlan - true: Request for a build plan, false: request for deployment project
     * @param securityToken - the security token for the Bamboo instance from which the request was received
     */
    public IsolatedDockerAgentRequest(final Configuration configuration, final String resultKey, final UUID uniqueIdentifier,
                                      final long originalQueingTimestamp, final String buildKey,
                                      final int retryCount, final boolean isPlan, final String securityToken) {
        this.configuration = configuration;
        this.resultKey = resultKey;
        this.uniqueIdentifier = uniqueIdentifier;
        this.queueTimestamp = originalQueingTimestamp;
        this.buildKey = buildKey;
        this.retryCount = retryCount;
        this.isPlan = isPlan;
        this.securityToken = securityToken;
    }

    public IsolatedDockerAgentRequest(final Configuration configuration, final String resultKey, final UUID uniqueIdentifier,
                                      final long originalQueingTimestamp, final String buildKey,
                                      final int retryCount, final boolean isPlan) {
        this.configuration = configuration;
        this.resultKey = resultKey;
        this.uniqueIdentifier = uniqueIdentifier;
        this.queueTimestamp = originalQueingTimestamp;
        this.buildKey = buildKey;
        this.retryCount = retryCount;
        this.isPlan = isPlan;
        this.securityToken = "";
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public String getResultKey() {
        return resultKey;
    }

    public UUID getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    public long getQueueTimestamp() {
        return queueTimestamp;
    }

    public String getBuildKey() {
        return buildKey;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean isPlan() {
        return isPlan;
    }

    public String getSecurityToken() {
        return securityToken;
    }
}