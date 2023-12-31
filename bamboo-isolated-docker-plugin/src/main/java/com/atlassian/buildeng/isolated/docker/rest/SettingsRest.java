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

package com.atlassian.buildeng.isolated.docker.rest;

import com.atlassian.buildeng.isolated.docker.GlobalConfiguration;
import com.google.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class SettingsRest {

    private final GlobalConfiguration configuration;

    @Inject
    public SettingsRest(GlobalConfiguration configuration) {
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
        c.setEnabled(configuration.getEnabledProperty());
        c.setDefaultImage(configuration.getDefaultImage());
        c.setAgentCleanupTime(configuration.getAgentCleanupTime());
        c.setAgentRemovalTime(configuration.getAgentRemovalTime());
        c.setMaxAgentCreationPerMinute(configuration.getMaxAgentCreationPerMinute());
        c.setArchitectureConfig(configuration.getArchitectureConfigAsString());
        c.setAwsVendor(GlobalConfiguration.VENDOR_AWS.equals(configuration.getVendor()));
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
            configuration.persist(config);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
        return Response.noContent().build();
    }
}
