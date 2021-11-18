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

package com.atlassian.buildeng.ecs.remote;

import com.atlassian.buildeng.ecs.remote.rest.Config;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import java.net.URISyntaxException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import static com.atlassian.buildeng.ecs.remote.ECSIsolatedAgentServiceImpl.createClient;

@WebSudoRequired
@Path("/")
public class Rest {

    private final GlobalConfiguration configuration;


    @Autowired
    public Rest(GlobalConfiguration configuration) {
        this.configuration = configuration;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response getConfig() {
        Config c = new Config();
        c.setAwsRole(configuration.getCurrentRole());
        c.setServerUrl(configuration.getCurrentServer());
        c.setSidekickImage(configuration.getCurrentSidekick());
        c.setPreemptiveScaling(configuration.isPreemptiveScaling());
        return Response.ok(c).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response setConfig(Config config) {
        if (StringUtils.isBlank(config.getServerUrl())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("serverUrl is mandatory").build();
        }
        if (StringUtils.isBlank(config.getSidekickImage())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("sidekickImage is mandatory").build();
        }
        configuration.persist(config);
        return Response.noContent().build();
    }

    static final String PARAM_CONTAINER = "containerName";
    static final String PARAM_TASK_ARN = "taskArn";

    @GET
    @Path("/logs")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAwsLogs(
            @QueryParam(PARAM_CONTAINER) String containerName,
            @QueryParam(PARAM_TASK_ARN) String taskArn)
    {
        if (containerName == null || taskArn == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(PARAM_CONTAINER + " and " + PARAM_TASK_ARN + " are mandatory").build();
        }
        String server = configuration.getCurrentServer();
        if (server == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("remote pbc server not defined in global settings.").build();
        }
        Client client = createClient();
        try {
            URIBuilder uriBuilder = new URIBuilder(configuration.getCurrentServer() + "/rest/logs")
                    .addParameter(Rest.PARAM_CONTAINER, containerName)
                    .addParameter(Rest.PARAM_TASK_ARN, taskArn);
            WebResource resource = client.resource(uriBuilder.build());
            return Response.ok().entity(resource
                    .accept(MediaType.TEXT_PLAIN_TYPE)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .get(String.class)).build();
        } catch (URISyntaxException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Error constructing URI to pbc-service").build();
        }
    }

}
