package com.atlassian.buildeng.kubernetes;

import com.amazonaws.util.StringUtils;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.exception.NotFoundException;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanType;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import io.fabric8.utils.ObjectUtils;

import java.util.Objects;

public class ExternalIdServiceImpl implements ExternalIdService {

    private final AdministrationConfigurationAccessor admConfAccessor;
    private final CachedPlanManager cachedPlanManager;
    private final DeploymentProjectService deploymentProjectService;

    private static final Integer IAM_REQUEST_LIMIT = 63;

    public ExternalIdServiceImpl(AdministrationConfigurationAccessor admConfAccessor,
                                 CachedPlanManager cachedPlanManager,
                                 DeploymentProjectService deploymentProjectService) {
        this.admConfAccessor = admConfAccessor;
        this.cachedPlanManager = cachedPlanManager;
        this.deploymentProjectService = deploymentProjectService;
    }

    @Override
    public String getExternalId(ImmutablePlan plan) {
        plan = plan.hasMaster() ? plan.getMaster() : plan;
        String externalId = getInstanceName() + "/" + plan.getPlanKey() + "/B/" + plan.getId();
        // IAM Request validator has a limit of 63 characters
        if (externalId.length() > IAM_REQUEST_LIMIT) {
            //Since the ID is unique, we need to make sure it's not truncate. The user-defined instance name and
            //plan need to be truncated.
            String toBeTruncated = getInstanceName() + "/" + plan.getPlanKey();

            //We need to truncate the string to fit a '/<ID>' at the end without going over the limit
            // -3 as we need to fit the '/B/'
            int endIndex = IAM_REQUEST_LIMIT - Objects.toString(plan.getId()).length() - 3;

            externalId = toBeTruncated.substring(0, endIndex) + "/B/" + plan.getId();
        }
        return externalId;
    }

    @Override
    public String getExternalId(DeploymentProject deploymentProject) {
        String externalId = getInstanceName() + "/" + deploymentProject.getPlanKey() + "/D/"
                + deploymentProject.getId();
        // IAM Request validator has a limit of 63 characters
        if (externalId.length() > IAM_REQUEST_LIMIT) {
            //Again, the ID is unique, so we should not truncate it. The user defined instance name and plan
            //key should be truncated instead.

            String toBeTruncated = getInstanceName() + "/" + deploymentProject.getPlanKey();

            //Truncate the instance-name to fit a '/D/<ID>' at the end.
            //-3 as we need to fit '/D/'

            int endIndex = IAM_REQUEST_LIMIT - Objects.toString(deploymentProject.getId()).length() - 3;

            externalId = toBeTruncated.substring(0, endIndex) + "/D/" + deploymentProject.getId();
        }

        return externalId;
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
