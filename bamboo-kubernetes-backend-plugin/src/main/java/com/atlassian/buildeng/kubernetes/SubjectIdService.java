package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;

public interface SubjectIdService {
    String getSubjectId(ImmutablePlan plan);

    String getSubjectId(DeploymentProject deploymentProject);

    String getSubjectId(PlanKey planKey);

    String getSubjectId(Long deploymentId);
}
