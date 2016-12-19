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
