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

package com.atlassian.buildeng.isolated.docker.events;

import com.atlassian.buildeng.ecs.scheduling.DockerHost;
import com.atlassian.buildeng.spi.isolated.docker.events.DockerAgentEvent;
import java.util.List;
import java.util.stream.Collectors;


public class DockerAgentEcsDisconnectedPurgeEvent extends DockerAgentEvent {

    private final List<DockerHost> selectedToKill;

    public DockerAgentEcsDisconnectedPurgeEvent(List<DockerHost> selectedToKill) {
        this.selectedToKill = selectedToKill;
    }

    @Override
    public String toString() {
        return "DockerAgentEcsDisconnectedPurgeEvent{"
                + "selectedToKill="
                + selectedToKill.stream().map(DockerHost::getInstanceId).collect(Collectors.toList())
                + '}';
    }

}
