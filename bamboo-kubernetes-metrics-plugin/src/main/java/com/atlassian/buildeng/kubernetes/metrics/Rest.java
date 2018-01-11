/*
 * Copyright 2016 - 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.metrics;

import com.atlassian.buildeng.kubernetes.metrics.rest.Config;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

@WebSudoRequired
@Path("/")
public class Rest {

    private final GlobalConfiguration configuration;


    @Autowired
    public Rest(GlobalConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * GET configuration rest endpoint.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response getConfig() {
        Config c = new Config();
        c.setPrometheusUrl(configuration.getPrometheusUrl() != null ? configuration.getPrometheusUrl() : "");
        return Response.ok(c).build();
    }

    /**
     * POST configuration rest endpoint.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response setConfig(Config config) {
        try {
            configuration.persist(config.getPrometheusUrl());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }
}
