package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.exception.NotFoundException;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanType;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;

public class ExternalIdServiceImpl implements ExternalIdService {

    private final AdministrationConfigurationAccessor admConfAccessor;
    private final CachedPlanManager cachedPlanManager;
    private final DeploymentProjectService deploymentProjectService;

    public ExternalIdServiceImpl(AdministrationConfigurationAccessor admConfAccessor,
                                 CachedPlanManager cachedPlanManager,
                                 DeploymentProjectService deploymentProjectService) {
        this.admConfAccessor = admConfAccessor;
        this.cachedPlanManager = cachedPlanManager;
        this.deploymentProjectService = deploymentProjectService;
    }

    @Override
    public String getExternalId(ImmutablePlan plan) {
        return getInstanceName()
            + ":" + plan.getPlanKey()
            + ":" + plan.getOid();
    }

    @Override
    public String getExternalId(DeploymentProject deploymentProject) {
        return getInstanceName()
            + ":" + deploymentProject.getId()
            + ":" + deploymentProject.getOid();
    }

    @Override
    public String getExternalId(PlanKey planKey) {
        ImmutablePlan plan = cachedPlanManager.getPlanByKey(planKey);
        if (plan == null) {
            throw new NotFoundException("Could not find plan with plankey: " + planKey.toString());
        } else if (plan.getPlanType().equals(PlanType.JOB)) {
            plan = ((ImmutableJob) plan).getParent();
        }
        return getExternalId(plan);
    }

    @Override
    public String getExternalId(Long deploymentId) {
        DeploymentProject deploymentProject = deploymentProjectService.getDeploymentProject(deploymentId);
        if (deploymentProject == null) {
            throw new NotFoundException("Could not find deployment project with id: " + deploymentId);
        }
        return getExternalId(deploymentProject);
    }

    private String getInstanceName() {
        return admConfAccessor.getAdministrationConfiguration().getInstanceName().toLowerCase().replaceAll("\\s", "-");
    }
}
