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
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.kubernetes.rest.Config;
import com.google.common.base.Throwables;
import java.io.IOException;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class Rest {

    private final GlobalConfiguration configuration;
    private final SubjectIdService subjectIdService;
    private final DeploymentProjectService deploymentProjectService;
    private BambooPermissionManager bambooPermissionManager;
    private final CachedPlanManager cachedPlanManager;
    private final BandanaManager bandanaManager;


    @Inject
    public Rest(GlobalConfiguration configuration,
                SubjectIdService subjectIdService,
                DeploymentProjectService deploymentProjectService,
                BambooPermissionManager bambooPermissionManager,
                CachedPlanManager cachedPlanManager,
                BandanaManager bandanaManager) {
        this.configuration = configuration;
        this.subjectIdService = subjectIdService;
        this.deploymentProjectService = deploymentProjectService;
        this.bambooPermissionManager = bambooPermissionManager;
        this.cachedPlanManager = cachedPlanManager;
        this.bandanaManager = bandanaManager;
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
        c.setArchitecturePodConfig(configuration.getBandanaArchitecturePodConfig());
        c.setIamRequestTemplate(configuration.getBandanaIamRequestTemplateAsString());
        c.setIamSubjectIdPrefix(configuration.getIamSubjectIdPrefix());
        c.setContainerSizes(configuration.getContainerSizesAsString());
        c.setPodLogsUrl(configuration.getPodLogsUrl());
        c.setUseClusterRegistry(configuration.isUseClusterRegistry());
        c.setClusterRegistryAvailableSelector(configuration.getClusterRegistryAvailableClusterSelector());
        c.setClusterRegistryPrimarySelector(configuration.getClusterRegistryPrimaryClusterSelector());
        c.setShowAwsSpecificFields(com.atlassian.buildeng.isolated.docker.GlobalConfiguration.VENDOR_AWS.equals(com.atlassian.buildeng.isolated.docker.GlobalConfiguration.getVendorWithBandana(bandanaManager)));
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
     * GET Subject ID used in roles for plans.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/subjectIdForPlan/{planKey}")
    public Response getSubjectIdPlan(@PathParam("planKey") String planKey) {
        try {
            PlanKey pk = PlanKeys.getPlanKey(planKey);
            ImmutablePlan plan = cachedPlanManager.getPlanByKey(pk);
            if (plan == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Can not found build plan with key: " + planKey).build();
            }
            if (bambooPermissionManager.hasPlanPermission(BambooPermission.READ, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.BUILD, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.WRITE, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.CLONE, pk)
                || bambooPermissionManager.hasPlanPermission(BambooPermission.ADMINISTRATION, pk)) {
                return Response.ok(configuration.getIamSubjectIdPrefix()
                        + subjectIdService.getSubjectId(plan)).build();
            } else {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity("You need at least View permission on this plan: " + planKey).build();
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Throwables.getStackTraceAsString(e)).build();
        }
    }

    /**
     * GET Subject ID used in roles for deployments, when obtained from anywhere besides an environment config page.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/subjectIdForDeployment/{deploymentId}")
    public Response getSubjectIdDeployment(@PathParam("deploymentId") Long deploymentId) {
        try {
            DeploymentProject deploymentProject =
                deploymentProjectService.getDeploymentProject(deploymentId);
            if (deploymentProject == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("Cannot find deployment project with ID: " + deploymentId).build();
            }
            return getSubjectIdDeploymentProject(deploymentProject, deploymentId);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Throwables.getStackTraceAsString(e)).build();
        }
    }

    /**
     * GET Subject ID used in roles for deployments, when obtained from inside an environment configuration page.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/subjectIdForDeploymentEnvironment/{environmentId}")
    public Response getSubjectIdDeploymentEnvironment(@PathParam("environmentId") Long environmentId) {
        try {
            DeploymentProject deploymentProject =
                    deploymentProjectService.getDeploymentProjectForEnvironment(environmentId);
            if (deploymentProject == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Cannot find deployment project for environment with ID: " + environmentId).build();
            }
            return getSubjectIdDeploymentProject(deploymentProject, deploymentProject.getId());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Throwables.getStackTraceAsString(e)).build();
        }
    }

    /**
     * GET Subject ID used in roles for deployments. Internal method for environment and deployment projects.
     */
    private Response getSubjectIdDeploymentProject(DeploymentProject deploymentProject, Long deploymentProjectId) {
        if (bambooPermissionManager.hasPermission(BambooPermission.READ, deploymentProject, null)
                || bambooPermissionManager.hasPermission(BambooPermission.WRITE, deploymentProject, null)
                || bambooPermissionManager.hasPermission(BambooPermission.CLONE, deploymentProject, null)
                || bambooPermissionManager.hasPermission(BambooPermission.ADMINISTRATION, deploymentProject, null)) {
            return Response.ok(configuration.getIamSubjectIdPrefix()
                    + subjectIdService.getSubjectId(deploymentProject)).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("You need at least View permission on this project: " + deploymentProjectId).build();
        }
    }

}
