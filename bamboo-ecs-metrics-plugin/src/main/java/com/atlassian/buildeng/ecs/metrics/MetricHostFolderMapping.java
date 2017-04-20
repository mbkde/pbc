/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.ecs.metrics;

import com.atlassian.buildeng.spi.isolated.docker.HostFolderMapping;

//TODO eventually these paths could be configurable in the UI.
public class MetricHostFolderMapping implements HostFolderMapping {

    @Override
    public String getVolumeName() {
        return "metrics";
    }

    @Override
    public String getHostPath() {
        return "/var/lib/docker/buildeng-metrics";
    }

    @Override
    public String getContainerPath() {
        return "/buildeng/metrics";
    }

}
