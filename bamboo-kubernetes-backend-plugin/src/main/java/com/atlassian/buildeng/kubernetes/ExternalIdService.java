package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;

public interface ExternalIdService {
    String getExternalId(ImmutablePlan plan);

    String getExternalId(DeploymentProject deploymentProject);

    String getExternalId(PlanKey planKey);

    String getExternalId(Long deploymentId);
}
