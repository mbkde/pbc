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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.v2.build.agent.capability.Capability;

public interface Constants {
    String RESULT_ERROR = "custom.isolated.docker.error"; // copied in ecs-plugin
    /**
     * marker custom data piece key set in StopDockerAgentBuildProcessor
     * when the agent was sentenced to die.
     * possible values 'true', 'false' or not defined.
     */
    String RESULT_AGENT_KILLED_ITSELF = "custom.isolated.docker.stopped";

    String CAPABILITY_RESULT = Capability.SYSTEM_PREFIX + ".isolated.docker.for";
    String EPHEMERAL_CAPABILITY_RESULT = Capability.SYSTEM_PREFIX + ".isolated.docker.ephemeral";
    /**
     * prefix for custom data passed from the api implementation.
     * Everything starting with this can end up in the UI.
     */
    String RESULT_PREFIX = "result.isolated.docker."; // copied in ecs-plugin

    /**
     * Key of the default architecture in the architecture list
     * {@link com.atlassian.buildeng.isolated.docker.rest.Config#architectureConfig}
     * This is used in determining the architecture a build should be used when unspecified.
     */
    public static final String DEFAULT_ARCHITECTURE = "default";

    /*
     * Ephemeral builds are enabled for PBC, as opposed to our
     * original implementation of hacking remote agents
     */
    String PBC_EPHEMERAL_ENABLED = "pbc.ephemeral.enabled";
}
