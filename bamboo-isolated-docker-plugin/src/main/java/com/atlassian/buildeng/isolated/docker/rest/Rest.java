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
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

@Path("/")
public class Rest {
    private final ElasticAccountBean elasticAccountBean;
    private final BandanaManager bandanaManager;
    private static boolean cacheValid = false;
    private Map<String, Integer> values;
    private final AmazonECSClient ecsClient;
    final ElasticConfiguration elasticConfig;

    @Autowired
    public Rest(BandanaManager bandanaManager, ElasticAccountBean elasticAccountBean) {
        this.bandanaManager = bandanaManager;
        this.elasticAccountBean = elasticAccountBean;
        this.elasticConfig = this.elasticAccountBean.getElasticConfig();
        assert this.elasticConfig != null;
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
        if (!cacheValid) {
            Map<String, Integer> values = (Map<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY);
            if (values != null) {
                this.values = values;
            } else {
                this.values = new TreeMap<>();
            }
            cacheValid = true;
        }
    }

    private void invalidateCache() {
        cacheValid = false;
    }

    // REST endpoints

    @GET
    @Produces({MediaType.TEXT_PLAIN})
    public Response getAll() {
        updateCache();
        return Response.ok("" + this.values).build();
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
                Iterator<Map.Entry<String, Integer>> it = values.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Integer> ent = it.next();
                    if (revision.equals(ent.getValue())) {
                        it.remove();
                        break;
                    }
                }
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