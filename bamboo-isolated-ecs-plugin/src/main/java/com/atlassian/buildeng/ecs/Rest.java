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

package com.atlassian.buildeng.ecs;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.fugue.Maybe;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Path("/")
public class Rest {
    private final ECSIsolatedAgentServiceImpl dockerAgent;

    @Autowired
    public Rest(BandanaManager bandanaManager, ECSIsolatedAgentServiceImpl dockerAgent) {
        this.dockerAgent = dockerAgent;
    }

    // REST endpoints

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAllDockerMappings() {
        return Response.ok(
            dockerAgent.getAllRegistrations().entrySet().stream().map((Map.Entry<String, Integer> entry) -> {
                HashMap<String, Object> obj = new HashMap<>();
                obj.put("dockerImage", entry.getKey());
                obj.put("revision", entry.getValue());
                return obj;
        }).toArray()).build();
    }

    @WebSudoRequired
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response create(String dockerImage) {
        return dockerAgent.registerDockerImage(dockerImage).fold(
                (String err) -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(err).build(),
                (Integer revision) -> Response.ok("" + revision).build());
    }

    @WebSudoRequired
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) {
        Maybe<String> result = dockerAgent.deregisterDockerImage(revision);
        if (result.isDefined()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result.get()).build();
        } else {
            return Response.ok().entity("OK").build();
        }
    }


    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/cluster")
    public Response getCluster() {
        return Response.ok().entity(dockerAgent.getCurrentCluster()).build();
    }

    @WebSudoRequired
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/cluster")
    public Response setCluster(String name) {
        dockerAgent.setCluster(name);
        return Response.ok().entity(name).build();
    }

    @WebSudoRequired
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster/valid")
    public Response getValidClusters() {
        return dockerAgent.getValidClusters().fold(
                (String ecsError) -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ecsError).build(),
                (Collection<String> clusters) -> Response.ok(clusters.toArray()).build());
    }
}

