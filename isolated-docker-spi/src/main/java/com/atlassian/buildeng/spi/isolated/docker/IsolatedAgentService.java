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

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     * participate in rendering of the job/plan result summary PBC page snippet.
     * @param configuration
     * @param customData
     * @return null if no logs available, html snippet otherwise
     */
    default String renderContainerLogs(Configuration configuration, Map<String, String> customData) {
        return null;
    }

}
