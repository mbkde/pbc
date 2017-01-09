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
package com.atlassian.buildeng.ecs.resources;

import com.atlassian.buildeng.ecs.api.Heartbeat;

import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsDisconnectedEvent;
import com.atlassian.event.api.EventPublisher;
import java.util.Collections;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
@Path("/healthcheck")
public class HeartBeatResource {

    @Inject
    public HeartBeatResource() {
    }

    @GET
    public Heartbeat returnHeartbeat()  {
        return new Heartbeat();
    }
}
