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
package com.atlassian.buildeng.simple.backend;

import com.atlassian.buildeng.simple.backend.rest.Config;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 *
 * @author mkleint
 */
@WebSudoRequired
@Path("/")
public class Rest {
 
    private final GlobalConfiguration globalConfiguration;

    public Rest(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response getConfig() {
        return Response.ok(globalConfiguration.getDockerConfig()).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response setConfig(Config config) {
        globalConfiguration.setDockerConfig(config);
        return Response.noContent().build();
    }

}