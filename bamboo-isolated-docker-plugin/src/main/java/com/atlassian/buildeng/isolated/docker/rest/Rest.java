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

package com.atlassian.buildeng.isolated.docker.rest;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.*;
import com.atlassian.bamboo.agent.elastic.server.ElasticAccountBean;
import com.atlassian.bamboo.agent.elastic.server.ElasticConfiguration;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Path("/")
public class Rest {
    private final ElasticAccountBean elasticAccountBean;
    private final BandanaManager bandanaManager;
    private static AtomicBoolean cacheValid = new AtomicBoolean(false);
    private ConcurrentMap<String, Integer> values = new ConcurrentHashMap<>();
    private final AmazonECSClient ecsClient;
    final ElasticConfiguration elasticConfig;

    @Autowired
    public Rest(BandanaManager bandanaManager, ElasticAccountBean elasticAccountBean) {
        this.bandanaManager = bandanaManager;
        this.elasticAccountBean = elasticAccountBean;
        this.elasticConfig = this.elasticAccountBean.getElasticConfig();
        if (this.elasticConfig == null) throw new AssertionError("failed to load elastic configuration");
        this.ecsClient = new AmazonECSClient(new BasicAWSCredentials(this.elasticConfig.getAwsAccessKeyId(), this.elasticConfig.getAwsSecretKey()));
        this.updateCache();
    }

    private static final String sidekickName       = "bamboo-agent-sidekick";
    private static final String agentName          = "bamboo-agent";
    private static final String taskDefinitionName = "staging-bamboo-generated";
    private static final String atlassianRegistry  = "docker.atlassian.io";

    private static final ContainerDefinition sidekickDefinition =
        new ContainerDefinition()
                .withName(sidekickName)
                .withImage(atlassianRegistry + "/" + sidekickName)
                .withCpu(10)
                .withMemory(512);

    private static final ContainerDefinition agentBaseDefinition =
        new ContainerDefinition()
                .withName(agentName)
                .withCpu(900)
                .withMemory(3072)
                .withVolumesFrom(new VolumeFrom().withSourceContainer(sidekickName));

    private static ContainerDefinition agentDefinition(String dockerImage) {
        return agentBaseDefinition.withImage(dockerImage);
    }

    private static RegisterTaskDefinitionRequest taskDefinitionRequest(String dockerImage) {
        return new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(agentDefinition(dockerImage), sidekickDefinition)
                .withFamily(taskDefinitionName);
    }

    private static DeregisterTaskDefinitionRequest deregisterTaskDefinitionRequest(Integer revision) {
        return new DeregisterTaskDefinitionRequest().withTaskDefinition(taskDefinitionName + ":" + revision);
    }

    // Caching/bandana

    private static final String KEY = "com.atlassian.buildeng.isolated.docker";

    private void updateCache() {
        if (cacheValid.compareAndSet(false, true)) {
            ConcurrentHashMap<String, Integer> values = (ConcurrentHashMap<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY);
            if (values != null) {
                this.values = values;
            }
        }
    }

    private void invalidateCache() {
        cacheValid.set(false);
    }

    // REST endpoints

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAll() {
        updateCache();
        return Response.ok(this.values).build();
    }

    @WebSudoRequired
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response create(String dockerImage) {
        updateCache();
        if (values.containsKey(dockerImage)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(dockerImage + " already exists").build();
        }
        final int revision;
        //call aws to get number.
        try {
            RegisterTaskDefinitionResult result = ecsClient.registerTaskDefinition(taskDefinitionRequest(dockerImage));
            revision = result.getTaskDefinition().getRevision();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("something blew up" + e).build();
        }
        values.put(dockerImage, revision);
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY, values);
        invalidateCache();
        return Response.ok("" + revision).build();
    }

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) {
        updateCache();
        if (values != null && values.containsValue(revision)) {
            try {
                ecsClient.deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
                values.values().remove(revision);
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY, values);
                invalidateCache();
                return Response.ok().build();
            } catch (Exception e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("something blew up" + e).build();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("revision " + revision + " does not exist").build();
        }
    }
}