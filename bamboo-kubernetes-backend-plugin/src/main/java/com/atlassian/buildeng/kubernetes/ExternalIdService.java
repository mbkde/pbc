package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.plan.Plan;

public interface ExternalIdService {
    String getExternalId(Plan plan);

    String getExternalId(DeploymentProject deploymentProject);
}
