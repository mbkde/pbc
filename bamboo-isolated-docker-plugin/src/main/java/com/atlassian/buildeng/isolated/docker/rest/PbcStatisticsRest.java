/*
 * Copyright 2017 Atlassian Pty Ltd.
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

import com.atlassian.bamboo.ResultKey;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jetbrains.annotations.NotNull;

@Path("/statistics")
public class PbcStatisticsRest {

    private final QueueManagerView<CommonContext, CommonContext> queueManagerView;

    public PbcStatisticsRest(@NotNull BuildQueueManager queueManager) {
        this.queueManagerView = QueueManagerView.newView(queueManager, (BuildQueueManager.QueueItemView<CommonContext> ctx) -> ctx);
    }

    /**
     *
     * @return Current queued pbc builds/deployments
     */
    @Path("/queuedBuilds")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public Response getQueuedPBCBuilds() {

        List<Map<String, String>> plans = Lists.newArrayList();
        List<Map<String, Object>> deployments = Lists.newArrayList();
        queueManagerView.getQueueView(Collections.emptyList()).forEach((BuildQueueManager.QueueItemView<CommonContext> item) ->{
            CommonContext ctx = item.getView();
            Configuration configuration = AccessConfiguration.forContext(ctx);
            if (configuration.isEnabled()) {
                BuildContext buildContext = Narrow.downTo(ctx, BuildContext.class);
                if (buildContext != null) {
                    ResultKey resultKey = buildContext.getResultKey();
                    PlanKey planKey = buildContext.getParentBuildContext().getTypedPlanKey();
                    plans.add(ImmutableMap.of("planKey", planKey.toString(), "resultKey", resultKey.toString()));
                } else {
                    DeploymentContext deploymentContext = Narrow.downTo(ctx, DeploymentContext.class);
                    if (deploymentContext != null) {
                        ResultKey resultKey = deploymentContext.getResultKey();
                        long projectId = deploymentContext.getDeploymentProjectId();
                        long environId = deploymentContext.getEnvironmentId();
                        long versionId = deploymentContext.getDeploymentVersion().getId();
                        deployments.add(ImmutableMap.of("projectId", projectId, "environmentId", environId, "versionId", versionId, "resultKey", resultKey.toString()));
                    }
                }
            }
        });

        return Response.ok().entity(ImmutableMap.of("plans", plans, "deployments", deployments)).build();
    }
}
