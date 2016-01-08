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
    public static final String CAPABILITY = Capability.SYSTEM_PREFIX + ".isolated.docker";

}
