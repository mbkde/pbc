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

package com.atlassian.buildeng.ecs;

public interface Constants extends com.atlassian.buildeng.ecs.scheduling.Constants {

    // The name used for the generated task definition (a.k.a. family)
    String TASK_DEFINITION_SUFFIX = "-generated";

    // The default cluster to use
    String DEFAULT_CLUSTER = "default";

    long PLUGIN_JOB_INTERVAL_MILLIS = 60000L; // Reap once every 60 seconds
    String PLUGIN_JOB_KEY = "ecs-watchdog";

    // these 2 copied from bamboo-isolated-docker-plugin to avoid dependency
    String RESULT_PREFIX = "result.isolated.docker.";
    String RESULT_ERROR = "custom.isolated.docker.error";
}
