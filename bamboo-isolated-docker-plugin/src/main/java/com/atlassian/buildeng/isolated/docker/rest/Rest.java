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

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.fugue.Maybe;
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
    private final BandanaManager bandanaManager;
    private static AtomicBoolean cacheValid = new AtomicBoolean(false);
    private final IsolatedAgentService dockerAgent;

    private ConcurrentMap<String, Integer> values = new ConcurrentHashMap<>();

    @Autowired
    public Rest(BandanaManager bandanaManager, IsolatedAgentService dockerAgent) {
        this.bandanaManager = bandanaManager;
        this.dockerAgent = dockerAgent;
        this.updateCache();
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
        //call aws to get number.
        return dockerAgent.registerDockerImage(dockerImage).fold(
                (String ecsError) -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Something in ECS blew up " + ecsError).build(),
                (Integer revision) -> {
                    values.put(dockerImage, revision);
                    bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY, values);
                    invalidateCache();
                    return Response.ok("" + revision).build();
                });
    }

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) {
        updateCache();
        if (values != null && values.containsValue(revision)) {
            Maybe<String> result = dockerAgent.deregisterDockerImage(revision);
            if (result.isDefined()) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("something blew up" + result.get()).build();
            } else {
                values.values().remove(revision);
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY, values);
                invalidateCache();
                return Response.ok().entity("OK").build();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity("revision " + revision + " does not exist").build();
        }
    }
}