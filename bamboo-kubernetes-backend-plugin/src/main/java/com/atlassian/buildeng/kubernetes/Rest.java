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

import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.buildeng.kubernetes.rest.Config;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.google.common.base.Throwables;
import java.io.IOException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

@WebSudoRequired
@Path("/")
public class Rest {

    private final GlobalConfiguration configuration;
    private final ExternalIdService externalIdService;
    private final DeploymentProjectService deploymentProjectService;
    private BambooPermissionManager bambooPermissionManager;
    private final CachedPlanManager cachedPlanManager;


    @Autowired
    public Rest(GlobalConfiguration configuration,
                ExternalIdService externalIdService,
                DeploymentProjectService deploymentProjectService,
                BambooPermissionManager bambooPermissionManager,
                PlanManager planManager, CachedPlanManager cachedPlanManager) {
        this.configuration = configuration;
        this.externalIdService = externalIdService;
        this.deploymentProjectService = deploymentProjectService;
        this.bambooPermissionManager = bambooPermissionManager;
        this.cachedPlanManager = cachedPlanManager;
    }

    /**
     * GET configuration rest endpoint.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/config")
    public Response getConfig() throws IOException {
        Config c = new Config();
        c.setSidekickImage(configuration.getCurrentSidekick());
        c.setCurrentContext(configuration.getCurrentContext());
        c.setPodTemplate(configuration.getPodTemplateAsString());
        c.setContainerSizes(configuration.getContainerSizesAsString());
        c.setPodLogsUrl(configuration.getPodLogsUrl());
        c.setUseClusterRegistry(configuration.isUseClusterRegistry());
        c.setClusterRegistryAvailableSelector(configuration.getClusterRegistryAvailableClusterSelector());
        c.setClusterRegistryPrimarySelector(configuration.getClusterRegistryPrimaryClusterSelector());
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
            configuration.persist(config.getSidekickImage(), config.getCurrentContext(), config.getPodTemplate(),
                config.getPodLogsUrl(), config.getContainerSizes(), config.isUseClusterRegistry(),
                config.getClusterRegistryAvailableSelector(), config.getClusterRegistryPrimarySelector());
        } catch (IllegalArgumentException | IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }

    /**
     * POST Kubernetes currentContext rest endpoint.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/config/currentContext")
    public Response setCurrentContext(String currentContext) {
        try {
            configuration.persistCurrentContext(currentContext);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }

    /**
     * GET externalID used in roles for plans
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/externalIdForPlan/{planKey}")
    public Response getExternalIdPlan(@PathParam("planKey") String planKey) {
        try {
            PlanKey pk = PlanKeys.getPlanKey(planKey);
            ImmutablePlan plan = cachedPlanManager.getPlanByKey(pk);
            if (plan == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Can not found build plan with key: " + planKey).build();
            }
            if (bambooPermissionManager.hasPlanPermission(BambooPermission.BUILD, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.WRITE, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.CLONE, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.ADMINISTRATION, pk)) {
                return Response.ok(externalIdService.getExternalId(plan)).build();

            } else {
                return Response.status(Response.Status.FORBIDDEN).entity("You need Build permission on this plan: " + planKey).build();
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Throwables.getStackTraceAsString(e)).build();
        }
    }

    /**
     * GET externalID used in roles for deployments
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/externalIdForDeployment/{deploymentId}")
    public Response getExternalIdDeployment(@PathParam("deploymentId") String deploymentId) {
        try {
            DeploymentProject deploymentProject = deploymentProjectService.getDeploymentProject(Long.parseLong(deploymentId));
            if (deploymentProject == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Can not found deployment project with id: " + deploymentId).build();
            }
            if (bambooPermissionManager.hasPermission(BambooPermission.BUILD, deploymentProject, null)
                || bambooPermissionManager.hasPermission(BambooPermission.WRITE, deploymentProject, null)
                || bambooPermissionManager.hasPermission(BambooPermission.CLONE, deploymentProject, null)
                || bambooPermissionManager.hasPermission(BambooPermission.ADMINISTRATION, deploymentProject, null)) {
                return Response.ok(externalIdService.getExternalId(deploymentProject)).build();
            } else {
                return Response.status(Response.Status.FORBIDDEN).entity("You need Build permission on this project: " + deploymentId).build();
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Throwables.getStackTraceAsString(e)).build();
        }
    }

}
