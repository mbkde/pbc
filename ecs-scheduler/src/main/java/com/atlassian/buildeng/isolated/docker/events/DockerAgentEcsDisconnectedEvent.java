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

package com.atlassian.buildeng.isolated.docker.events;

import com.atlassian.buildeng.ecs.scheduling.DockerHost;
import java.util.Collection;
import java.util.stream.Collectors;


public class DockerAgentEcsDisconnectedEvent {

    private final Collection<DockerHost> disconnected;

    public DockerAgentEcsDisconnectedEvent(Collection<DockerHost> disconnected) {
        this.disconnected = disconnected;
    }

    @Override
    public String toString() {
        return "DockerAgentEcsDisconnectedEvent{" + "disconnectedAgents=" + disconnected.stream().map((DockerHost t) -> t.getInstanceId()).collect(Collectors.toList()) + '}';
    }

}
