package com.atlassian.buildeng.ecs.resources;

import com.atlassian.buildeng.ecs.api.Heartbeat;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Created by ojongerius on 09/05/2016.
 */

@Produces(MediaType.APPLICATION_JSON)
@Path("/healthcheck")
public class HeartBeatResource {
    public HeartBeatResource() {
    }

    @GET
    public Heartbeat returnHeartbeat()  {
        return new Heartbeat();
    }
}
