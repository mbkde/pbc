package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanManager;
import javax.validation.constraints.NotNull;

public class ExternalIdServiceImpl implements ExternalIdService {

    private final AdministrationConfigurationAccessor admConfAccessor;

    public ExternalIdServiceImpl(PlanManager planManager, AdministrationConfigurationAccessor admConfAccessor) {
        this.admConfAccessor = admConfAccessor;
    }

    @Override
    @NotNull
    public String getExternalId(Plan plan) {
        return admConfAccessor.getAdministrationConfiguration().getInstanceName()+":"+plan.getPlanKey()+":"+plan.getOid();

    }

    @Override
    public String getExternalId(DeploymentProject deploymentProject) {
        return admConfAccessor.getAdministrationConfiguration().getInstanceName() + ":" + deploymentProject.getId() + ":" + deploymentProject.getOid();
    }
}
