/*
 * Copyright 2015 Atlassian.
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

public final class IsolatedDockerAgentRequest {

    private final String taskDefinitionName;
    private final String buildResultKey;
    private final String cluster;

    /**
     *
     * @param identifier - identifier of what to execute, in ecs this translates to Task Definition name
     * @param buildResultKey
     * @param cluster
     */
    public IsolatedDockerAgentRequest(String identifier, String buildResultKey, String cluster) {
        this.taskDefinitionName = identifier;
        this.buildResultKey = buildResultKey;
        this.cluster = cluster;
    }

    public String getTaskDefinition() {
        return taskDefinitionName;
    }


    public String getBuildResultKey() {
        return buildResultKey;
    }

    public String getCluster() {
        return cluster;
    }

}
