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

package com.atlassian.buildeng.isolated.docker.rest;

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/ui")
public class UIRest {

    private final CachedPlanManager cpm;
    private final IsolatedAgentService isolatedAgentService;

    @Inject
    public UIRest(CachedPlanManager cpm, IsolatedAgentService isolatedAgentService) {
        this.cpm = cpm;
        this.isolatedAgentService = isolatedAgentService;
    }

    @GET
    @Path("/enabled")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isDockerEnabled(@QueryParam("jobKey") String jobKey) {
        if (jobKey == null) {
            return Response.status(Status.BAD_REQUEST).entity("JobKey query parameter not defined").build();
        }
        PlanKey key = PlanKeys.getPlanKey(jobKey);
        ImmutablePlan job = cpm.getPlanByKey(key);
        if (job == null) {
            return Response.status(Status.BAD_REQUEST).entity("Job not found").build();
        }
        ImmutableJob jb = Narrow.downTo(job, ImmutableJob.class);
        if (jb == null) {
            return Response.status(Status.BAD_REQUEST).entity("Not a Job Key").build();
        }
        Configuration conf = AccessConfiguration.forJob(jb);
        JSONObject toRet = new JSONObject();
        toRet.put("enabled", conf.isEnabled());
        JSONArray result = new JSONArray();
        toRet.put("tasks", result);
        if (conf.isEnabled()) {
            jb.getTaskDefinitions().stream().forEach((TaskDefinition t) -> {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id", t.getId());
                    // runtime in nodejs, label evenrywhere else?
                    String lbl = t.getConfiguration().getOrDefault("label", t.getConfiguration().get("runtime"));
                    obj.put("label", lbl);
                    obj.put("buildJdk", t.getConfiguration().get("buildJdk"));
                    result.put(obj);
                } catch (JSONException ex) {
                    Logger.getLogger(UIRest.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
        return Response.ok().entity(toRet.toString()).build();
    }

    @GET
    @Path("/knownImages")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getKnownImages() {
        return isolatedAgentService.getKnownDockerImages();
    }
}
