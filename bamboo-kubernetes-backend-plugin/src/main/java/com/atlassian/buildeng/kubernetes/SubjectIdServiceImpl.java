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

public class SubjectIdServiceImpl implements SubjectIdService {

    private final AdministrationConfigurationAccessor admConfAccessor;
    private final CachedPlanManager cachedPlanManager;
    private final DeploymentProjectService deploymentProjectService;

    private static final Integer IAM_REQUEST_LIMIT = 63;

    public SubjectIdServiceImpl(AdministrationConfigurationAccessor admConfAccessor,
                                CachedPlanManager cachedPlanManager,
                                DeploymentProjectService deploymentProjectService) {
        this.admConfAccessor = admConfAccessor;
        this.cachedPlanManager = cachedPlanManager;
        this.deploymentProjectService = deploymentProjectService;
    }

    @Override
    public String getSubjectId(ImmutablePlan plan) {
        plan = plan.hasMaster() ? plan.getMaster() : plan;
        // Check if plan is a "job" that has been casted to ImmutablePlan; get the containing plan if so.
        plan = plan.getPlanType().equals(PlanType.JOB) ? ((ImmutableJob) plan).getParent() : plan;
        String subjectId = getInstanceName() + "/" + plan.getPlanKey() + "/B/" + plan.getId();
        // IAM Request validator has a limit of 63 characters
        if (subjectId.length() > IAM_REQUEST_LIMIT) {
            //Since the ID is unique, we need to make sure it's not truncate. The user-defined instance name and
            //plan need to be truncated.
            String toBeTruncated = getInstanceName() + "/" + plan.getPlanKey();

            //We need to truncate the string to fit a '/<ID>' at the end without going over the limit
            // -3 as we need to fit the '/B/'
            int endIndex = IAM_REQUEST_LIMIT - String.valueOf(plan.getId()).length() - 3;

            subjectId = toBeTruncated.substring(0, endIndex) + "/B/" + plan.getId();
        }
        return subjectId;
    }

    @Override
    public String getSubjectId(DeploymentProject deploymentProject) {
        String subjectId = getInstanceName() + "/" + deploymentProject.getPlanKey() + "/D/"
                + deploymentProject.getId();
        // IAM Request validator has a limit of 63 characters
        if (subjectId.length() > IAM_REQUEST_LIMIT) {
            //Again, the ID is unique, so we should not truncate it. The user defined instance name and plan
            //key should be truncated instead.

            String toBeTruncated = getInstanceName() + "/" + deploymentProject.getPlanKey();

            //Truncate the instance-name to fit a '/D/<ID>' at the end.
            //-3 as we need to fit '/D/'

            int endIndex = IAM_REQUEST_LIMIT - String.valueOf(deploymentProject.getId()).length() - 3;

            subjectId = toBeTruncated.substring(0, endIndex) + "/D/" + deploymentProject.getId();
        }

        return subjectId;
    }

    @Override
    public String getSubjectId(PlanKey planKey) {
        ImmutablePlan plan = cachedPlanManager.getPlanByKey(planKey);
        if (plan == null) {
            throw new NotFoundException("Could not find plan with plankey: " + planKey.toString());
        }
        return getSubjectId(plan);
    }

    @Override
    public String getSubjectId(Long deploymentId) {
        DeploymentProject deploymentProject = deploymentProjectService.getDeploymentProject(deploymentId);
        if (deploymentProject == null) {
            throw new NotFoundException("Could not find deployment project with id: " + deploymentId);
        }
        return getSubjectId(deploymentProject);
    }

    private String getInstanceName() {
        return admConfAccessor.getAdministrationConfiguration().getInstanceName().toLowerCase().replaceAll("\\s", "-");
    }
}
