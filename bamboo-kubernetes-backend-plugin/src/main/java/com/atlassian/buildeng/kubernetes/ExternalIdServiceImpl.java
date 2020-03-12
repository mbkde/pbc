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
        String externalId = getInstanceName() + "/" + plan.getPlanKey() + "/" + plan.getOid();
        // IAM Request validator has a limit of 63 characters
        if (externalId.length() > IAM_REQUEST_LIMIT) {
            //Since the OID is unique, we need to make sure it's not truncate. The user-defined instance name and
            //plan need to be truncated.
            String toBeTruncated = getInstanceName() + "/" + plan.getPlanKey();

            //We need to truncate the string to fit a '/<OID>' at the end without going over the limit
            // -1 as we need to fit the '/'
            int endIndex = IAM_REQUEST_LIMIT - plan.getOid().toString().length() - 1;

            externalId = toBeTruncated.substring(0, endIndex) + "/" + plan.getOid();
        }
        return externalId;
    }

    @Override
    public String getExternalId(DeploymentProject deploymentProject) {
        String externalID = getInstanceName() + "/" + deploymentProject.getId() + "/" + deploymentProject.getOid();
        // IAM Request validator has a limit of 63 characters
        if (externalID.length() > IAM_REQUEST_LIMIT) {
            //Both the deploymentID and OID should not be truncated. Only the instance-name should be truncated

            //Truncate the instance-name to fit a '/<DEPLOYMENT_ID>/<OID>' at the end.
            //-2 as we need to fit two '/'
            int endIndex = IAM_REQUEST_LIMIT - deploymentProject.getOid().toString().length()
                - StringUtils.fromLong(deploymentProject.getId()).length() - 2;

            externalID = getInstanceName().substring(0, endIndex)
                + "/" + deploymentProject.getId() + "/" + deploymentProject.getOid();
        }

        return externalID;
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
