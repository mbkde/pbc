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

import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.atlassian.buildeng.ecs.exceptions.RestableIsolatedDockerException;
import com.atlassian.buildeng.ecs.rest.DockerMapping;
import com.atlassian.buildeng.ecs.rest.GetAllImagesResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentClusterResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentSidekickResponse;
import com.atlassian.buildeng.ecs.rest.GetValidClustersResponse;
import com.atlassian.buildeng.ecs.rest.RegisterImageResponse;
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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebSudoRequired
@Path("/")
public class Rest {
    private final ECSIsolatedAgentServiceImpl dockerAgent;

    @Autowired
    public Rest(ECSIsolatedAgentServiceImpl dockerAgent) {
        this.dockerAgent = dockerAgent;
    }

    // REST endpoints

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllDockerMappings() {
        Map<String, Integer> mappings = dockerAgent.getAllRegistrations();
        return Response.ok(new GetAllImagesResponse(mappings.entrySet().stream().map(
                (Map.Entry<String, Integer> entry) -> new DockerMapping(entry.getKey(), entry.getValue())
        ).collect(Collectors.toList()))).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(String requestString) throws RestableIsolatedDockerException {
        String dockerImage;
        try {
            JSONObject o = new JSONObject(requestString);
            dockerImage = o.getString("dockerImage");
        } catch (JSONException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.toString()).type("text/plain").build();
        }
        if (dockerImage == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'dockerImage' field").build();
        }
        Integer revision = dockerAgent.registerDockerImage(dockerImage);
        return Response.ok(new RegisterImageResponse(revision)).build();
    }

    @DELETE
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) throws RestableIsolatedDockerException {
        dockerAgent.deregisterDockerImage(revision);
        return Response.noContent().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster")
    public Response getCurrentCluster() {
        return Response.ok(new GetCurrentClusterResponse(dockerAgent.getCurrentCluster())).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/cluster")
    public Response setCluster(String requestString) {
        String cluster;
        try {
            JSONObject o = new JSONObject(requestString);
            cluster = o.getString("cluster");
        } catch (JSONException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.toString()).build();
        }
        if (cluster == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'cluster' field").build();
        }
        dockerAgent.setCluster(cluster);
        return Response.created(URI.create("/cluster")).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/sidekick")
    public Response getCurrentSidekick() {
        return Response.ok(new GetCurrentSidekickResponse(dockerAgent.getCurrentSidekick())).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/sidekick")
    public Response setSidekick(String requestString) {
        String sidekick;
        try {
            JSONObject o = new JSONObject(requestString);
            sidekick = o.getString("sidekick");
        } catch (JSONException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.toString()).build();
        }
        if (sidekick == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'sidekick' field").build();
        }
        dockerAgent.setSidekick(sidekick);
        return Response.noContent().build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/sidekick/reset")
    public Response resetSidekick() {
        dockerAgent.resetSidekick();
        return Response.noContent().build();
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster/valid")
    public Response getValidClusters() throws RestableIsolatedDockerException {
        List<String> clusters = dockerAgent.getValidClusters();
        return Response.ok(new GetValidClustersResponse(clusters)).build();
    }
}