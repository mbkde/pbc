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

package com.atlassian.buildeng.ecs;

import com.atlassian.buildeng.ecs.rest.JobsUsingImageResponse;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.buildeng.ecs.exceptions.RestableIsolatedDockerException;
import com.atlassian.buildeng.ecs.logs.AwsLogs;
import com.atlassian.buildeng.ecs.rest.Config;
import com.atlassian.buildeng.ecs.rest.DockerMapping;
import com.atlassian.buildeng.ecs.rest.GetAllImagesResponse;
import com.atlassian.buildeng.ecs.rest.GetValidClustersResponse;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import java.io.OutputStream;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import org.apache.commons.lang3.StringUtils;

@WebSudoRequired
@Path("/")
public class Rest {
    private final GlobalConfiguration configuration;
    private final CachedPlanManager cachedPlanManager;
    private final TaskDefinitionRegistrations taskDefRegistrations;


    @Autowired
    public Rest(GlobalConfiguration configuration, CachedPlanManager cachedPlanManager, TaskDefinitionRegistrations taskDefRegistrations) {
        this.configuration = configuration;
        this.cachedPlanManager = cachedPlanManager;
        this.taskDefRegistrations = taskDefRegistrations;
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
        c.setEnvs(configuration.getEnvVars());
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
        if (StringUtils.isBlank(config.getAutoScalingGroupName())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("autoScalingGroupName is mandatory").build();
        }
        if (StringUtils.isBlank(config.getEcsClusterName())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("ecsClusterName is mandatory").build();
        }
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
                    Configuration config = forJob(job);
                    if (config.isEnabled()) {
                        if (revision == taskDefRegistrations.findTaskRegistrationVersion(config, configuration)) {
                            toRet.add(new JobsUsingImageResponse.JobInfo(job.getName(), job.getKey()));
                        }
                    }
                });
        return Response.ok(new JobsUsingImageResponse(toRet)).build();
    }


    //constants for /awslogs query params
    static final String PARAM_TASK_ARN = "taskArn";
    static final String PARAM_CONTAINER = "container";

    @GET
    @Path("/logs")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAwsLogs(@QueryParam(PARAM_CONTAINER) String containerName,
                               @QueryParam(PARAM_TASK_ARN) String taskArn) {
        if (containerName == null || taskArn == null) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
        String driver = configuration.getLoggingDriver();
        if ("awslogs".equals(driver)) {
            String region = configuration.getLoggingDriverOpts().get("awslogs-region");
            String logGroupName = configuration.getLoggingDriverOpts().get("awslogs-group");
            String prefix = configuration.getLoggingDriverOpts().get("awslogs-stream-prefix");
            if (region == null || logGroupName == null || prefix == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("For awslogs docker log driver, all of 'awslogs-region', 'awslogs-group' and 'awslogs-stream-prefix' have to be defined.").build();
            }

            StreamingOutput stream = (OutputStream os) -> {
                AwsLogs.writeTo(os, logGroupName, region, prefix, containerName, taskArn);
            };
            return Response.ok(stream).build();
        }
        return Response.ok().build();
    }
    
    //copy of AccessConfiguration from the other plugin.
    // it's a copy to make the isolated-docker-spi plugin lightweight without 
    // significant refs to bamboo
    public static Configuration forJob(ImmutableJob job) {
        Map<String, String> cc = job.getBuildDefinition().getCustomConfiguration();
        return forMap(cc);
    }
    
    @Nonnull
    private static Configuration forMap(@Nonnull Map<String, String> cc) {
        return ConfigurationBuilder.create(cc.getOrDefault(Configuration.DOCKER_IMAGE, ""))
                    .withEnabled(Boolean.parseBoolean(cc.getOrDefault(Configuration.ENABLED_FOR_JOB, "false")))
                    .withImageSize(Configuration.ContainerSize.valueOf(cc.getOrDefault(Configuration.DOCKER_IMAGE_SIZE, Configuration.ContainerSize.REGULAR.name())))
                    .withExtraContainers(ConfigurationPersistence.fromJsonString(cc.getOrDefault(Configuration.DOCKER_EXTRA_CONTAINERS, "[]")))
                    .build();
    }
}