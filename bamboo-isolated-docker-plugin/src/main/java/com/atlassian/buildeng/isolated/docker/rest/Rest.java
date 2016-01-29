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
import com.atlassian.bandana.BandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.plugin.webresource.Tuple;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/xxx")
public class Rest {

    private final BandanaManager bandanaManager;

    public Rest(BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }


    @GET
    @Produces({MediaType.TEXT_PLAIN})
    public Response getAll() {
        Map<String, Integer> values = (Map<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY);
       return Response.ok("" + values).build();
    }

    @WebSudoRequired
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response create(String dockerImage) {
        Map<String, Integer> values = (Map<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY);
        if (values == null) {
            values = new TreeMap<>();
        }
        if (values.containsKey(dockerImage)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Already exists XXX").build();
        }
        final int revision = new Random().nextInt();
        //call aws to get number.
        values.put(dockerImage, revision);
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY, values);
        return Response.ok("" + revision).build();
    }
    private static final String KEY = "com.atlassian.buildeng.isolated.docker";

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/{revision}")
    public Response delete(@PathParam("revision") Integer revision) {
        Map<String, Integer> values = (Map<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY);
        if (values != null) {
            Iterator<Map.Entry<String, Integer>> it = values.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> ent = it.next();
                if (revision.equals(ent.getValue())) {
                   it.remove();
                   break;
                }
            }
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, KEY, values);
        }
       return Response.ok().build();
    }

}
