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
    public static final String ENABLED_FOR_JOB = "custom.isolated.docker.enabled";
    public static final String DOCKER_IMAGE = "custom.isolated.docker.image";
    public static final String RESULT_ERROR = "custom.isolated.docker.error";
    public static final String CAPABILITY = Capability.SYSTEM_PREFIX + ".isolated.docker";
    public static final long   REAPER_THRESHOLD_MILLIS = 300000L; //Reap agents if they're older than 5 minutes
    public static final long   REAPER_INTERVAL_MILLIS  =  30000L; //Reap once every 30 seconds
    public static final String REAPER_KEY = "isolated-docker-reaper";
    public static final String REAPER_AGENT_MANAGER_KEY = "reaper-agent-manager";
    public static final String REAPER_AGENTS_HELPER_KEY = "reaper-agents-helper";
    public static final String REAPER_COMMAND_SENDER_KEY = "reaper-command-sender";
    public static final String REAPER_DEATH_LIST = "reaper-death-list";
}
