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

package com.atlassian.buildeng.kubernetes;

import com.atlassian.buildeng.kubernetes.rest.Config;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

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
        c.setAwsRole(configuration.getIAMRole());
        c.setServerUrl(configuration.getKubernetesURL());
        c.setSidekickImage(configuration.getCurrentSidekick());
        c.setNamespace(configuration.getKubernetesNamespace());
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
        if (StringUtils.isBlank(config.getNamespace())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("namespace is mandatory").build();
        }
        configuration.persist(config.getSidekickImage(), config.getAwsRole(), config.getServerUrl(), config.getNamespace());
        return Response.noContent().build();
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/template")
    public Response getTemplate() {
        String template = configuration.getPodTemplateAsString();
        if (template == null) {
            template = "apiVersion: v1\n" +
                        "kind: Pod";
        }
        return Response.ok(template).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/template")
    public Response setTemplate(String template) {
        configuration.persistTemplate(template);
        return Response.noContent().build();
    }

}
