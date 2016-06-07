/*
 * Copyright 2016 Atlassian.
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
    String RESULT_ERROR = "custom.isolated.docker.error"; //copied in ecs-plugin
    /**
     * marker custom data piece key set in StopDockerAgentBuildProcessor
     * when the agent was sentenced to die
     */
    String RESULT_AGENT_DEATH_KISS = "custom.isolated.docker.stopped";
    String CAPABILITY = Capability.SYSTEM_PREFIX + ".isolated.docker";
    String CAPABILITY_RESULT = Capability.SYSTEM_PREFIX + ".isolated.docker.for";
    long   REAPER_THRESHOLD_MILLIS = 300000L; //Reap agents if they're older than 5 minutes
    long   REAPER_INTERVAL_MILLIS  =  30000L; //Reap once every 30 seconds
    String REAPER_KEY = "isolated-docker-reaper";
    String REAPER_AGENT_MANAGER_KEY = "reaper-agent-manager";
    String REAPER_AGENTS_HELPER_KEY = "reaper-agents-helper";
    String REAPER_COMMAND_SENDER_KEY = "reaper-command-sender";
    String REAPER_DEATH_LIST = "reaper-death-list";
    /**
     * prefix for custom data passed from the api implementation.
     * Everything starting with this can end up in the UI.
     */
    String RESULT_PREFIX = "result.isolated.docker."; //copied in ecs-plugin

    
}
