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

import com.atlassian.bamboo.Key;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public interface IsolatedAgentService {
    /**
     * Start an isolated docker agent to handle the build request
     *
     * @param request - request object
     * @param callback callback to process the result
     */
    void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback);

   /**
    * optional method listing all known docker images for use in the UI
    */
    default List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    /**
     * provide links to container logs for the configuration and specific run data.
     * @param configuration
     * @param customData implementation specific custom data as returned by IsolatedDockerAgentResult
     * @return never null, always a map with container name: link key-value pairs
     */
    @NotNull
    default Map<String, URL> getContainerLogs(Configuration configuration, Map<String, String> customData) {
        return Collections.emptyMap();
    }

    /**
     * optional way to announce future requirements
     * @param buildKey
     * @param jobResultKeys
     * @param excessMemoryCapacity
     * @param excessCpuCapacity
     */
    default void reserveCapacity(Key buildKey, List<String> jobResultKeys, long excessMemoryCapacity, long excessCpuCapacity) {
    }

}
