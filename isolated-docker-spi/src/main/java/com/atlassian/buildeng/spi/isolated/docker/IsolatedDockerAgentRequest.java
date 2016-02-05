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

    private final String dockerImage;
    private final String buildResultKey;

    /**
     * @param dockerImage    - image for isolated docker agent to use
     * @param buildResultKey
     */
    public IsolatedDockerAgentRequest(String dockerImage, String buildResultKey) {
        this.dockerImage = dockerImage;
        this.buildResultKey = buildResultKey;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public String getBuildResultKey() {
        return buildResultKey;
    }

}