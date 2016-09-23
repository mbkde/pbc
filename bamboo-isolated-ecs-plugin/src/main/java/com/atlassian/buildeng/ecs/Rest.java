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
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.buildeng.ecs.exceptions.RestableIsolatedDockerException;
import com.atlassian.buildeng.ecs.rest.Config;
import com.atlassian.buildeng.ecs.rest.DockerMapping;
import com.atlassian.buildeng.ecs.rest.GetAllImagesResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentASGResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentClusterResponse;
import com.atlassian.buildeng.ecs.rest.GetCurrentSidekickResponse;
import com.atlassian.buildeng.ecs.rest.GetValidClustersResponse;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

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
    @Path("/cluster/valid")
    @Deprecated
    public Response getValidClusters() throws RestableIsolatedDockerException {
        List<String> clusters = configuration.getValidClusters();
        return Response.ok(new GetValidClustersResponse(clusters)).build();
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response getConfig() {
        Config c = new Config();
        c.setAutoScalingGroupName(configuration.getCurrentASG());
        c.setEcsClusterName(configuration.getCurrentCluster());
        c.setSidekickImage(configuration.getCurrentSidekick());
        String driver = configuration.getLoggingDriver();
        if (driver != null) {
            Config.LogConfiguration lc = new Config.LogConfiguration();
            lc.setDriver(driver);
            lc.setOptions(configuration.getLoggingDriverOpts());
            c.setLogConfiguration(lc);
        }
        return Response.ok(c).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response setConfig(Config config) {
        configuration.setConfig(config);
        return Response.noContent().build();
    }
    
    @GET
    @Path("/usages/{revision}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUsages(@PathParam("revision") final int revision) {
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