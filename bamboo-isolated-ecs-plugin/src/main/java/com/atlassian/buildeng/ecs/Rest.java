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

import com.atlassian.buildeng.ecs.rest.JobsUsingImageResponse;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.buildeng.ecs.exceptions.RestableIsolatedDockerException;
import com.atlassian.buildeng.ecs.rest.DockerMapping;
import com.atlassian.buildeng.ecs.rest.GetAllImagesResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentASGResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentClusterResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentSidekickResponse;
import com.atlassian.buildeng.ecs.rest.GetValidClustersResponse;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
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
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@WebSudoRequired
@Path("/")
public class Rest {
    private final GlobalConfiguration configuration;
    private final CachedPlanManager cachedPlanManager;

    @Autowired
    public Rest(GlobalConfiguration configuration, CachedPlanManager cachedPlanManager) {
        this.configuration = configuration;
        this.cachedPlanManager = cachedPlanManager;
    }

    // REST endpoints

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllDockerMappings() {
        Map<Configuration, Integer> mappings = configuration.getAllRegistrations();
        return Response.ok(new GetAllImagesResponse(mappings.entrySet().stream().map(
                (Entry<Configuration, Integer> entry) -> new DockerMapping(entry.getKey().getDockerImage(), entry.getValue())
        ).collect(Collectors.toList()))).build();
    }

    @DELETE
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) throws RestableIsolatedDockerException {
        configuration.deregisterDockerImage(revision);
        return Response.noContent().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster")
    public Response getCurrentCluster() {
        return Response.ok(new GetCurrentClusterResponse(configuration.getCurrentCluster())).build();
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
            return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
        }
        if (cluster == null) {
            return Response.status(Status.BAD_REQUEST).entity("Missing 'cluster' field").build();
        }
        configuration.setCluster(cluster);
        return Response.created(URI.create("/cluster")).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/sidekick")
    public Response getCurrentSidekick() {
        return Response.ok(new GetCurrentSidekickResponse(configuration.getCurrentSidekick())).build();
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
            return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
        }
        if (sidekick == null) {
            return Response.status(Status.BAD_REQUEST).entity("Missing 'sidekick' field").build();
        }
        Collection<Exception> exceptions = configuration.setSidekick(sidekick);
        if (exceptions.isEmpty()) {
            return Response.noContent().build();
        } else {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(exceptions.toString()).build();
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/sidekick/reset")
    public Response resetSidekick() {
        Collection<Exception> exceptions = configuration.resetSidekick();
        if (exceptions.isEmpty()) {
            return Response.noContent().build();
        } else {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(exceptions.toString()).build();
        }
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cluster/valid")
    public Response getValidClusters() throws RestableIsolatedDockerException {
        List<String> clusters = configuration.getValidClusters();
        return Response.ok(new GetValidClustersResponse(clusters)).build();
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/asg")
    public Response getCurrentASG() {
        return Response.ok(new GetCurrentASGResponse(configuration.getCurrentASG())).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/asg")
    public Response setASG(String requestString) {
        String asg;
        try {
            JSONObject o = new JSONObject(requestString);
            asg = o.getString("asg");
        } catch (JSONException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
        }
        if (asg == null) {
            return Response.status(Status.BAD_REQUEST).entity("Missing 'asg' field").build();
        }
        configuration.setCurrentASG(asg);
        return Response.noContent().build();
    }
    
    @GET
    @Path("/usages/{revision}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUsages(@PathParam("revision") int revision) {
        //TODO environments
        List<JobsUsingImageResponse.JobInfo> toRet = new ArrayList<>();
        cachedPlanManager.getPlans(ImmutableJob.class).stream()
                .filter(job -> !job.hasMaster())
                .forEach(job -> {
                    Configuration config = Configuration.forJob(job);
                    if (config.isEnabled()) {
                        if (revision == configuration.findTaskRegistrationVersion(config)) {
                            toRet.add(new JobsUsingImageResponse.JobInfo(job.getName(), job.getKey()));
                        }
                    }
                });
        return Response.ok(new JobsUsingImageResponse(toRet)).build();
    }
    
}