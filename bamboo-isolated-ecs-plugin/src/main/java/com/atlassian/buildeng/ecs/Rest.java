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
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.RevisionNotActiveException;
import com.atlassian.buildeng.ecs.rest.DockerMapping;
import com.atlassian.buildeng.ecs.rest.GetAllImagesResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentClusterResponse;
import com.atlassian.buildeng.ecs.rest.GetValidClustersResponse;
import com.atlassian.buildeng.ecs.rest.RegisterImageResponse;
import com.atlassian.buildeng.ecs.rest.SetClusterResponse;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @WebSudoRequired
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(String requestString) {
        String dockerImage = null;
        try {
            JSONObject o = new JSONObject(requestString);
            dockerImage = o.getString("dockerImage");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (dockerImage == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing 'dockerImage' field").build();
        }
        try {
            Integer revision = dockerAgent.registerDockerImage(dockerImage);
            return Response.ok(new RegisterImageResponse(revision)).build();
        } catch (ImageAlreadyRegisteredException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.toString()).build();
        } catch (ECSException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
        }
    }

    @WebSudoRequired
    @DELETE
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) {
        try {
            dockerAgent.deregisterDockerImage(revision);
            return Response.noContent().build();
        } catch (RevisionNotActiveException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.toString()).build();
        } catch (ECSException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster")
    public Response getCurrentCluster() {
        return Response.ok(new GetCurrentClusterResponse(dockerAgent.getCurrentCluster())).build();
    }

    @WebSudoRequired
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
        return Response.ok(new SetClusterResponse(dockerAgent.getCurrentCluster())).build();
    }

    @WebSudoRequired
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster/valid")
    public Response getValidClusters() {
        try {
            List<String> clusters = dockerAgent.getValidClusters();
            return Response.ok(new GetValidClustersResponse(clusters)).build();
        } catch (ECSException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
        }

    }
}