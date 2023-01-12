package com.atlassian.pbc;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.owner.PlanOwner;
import com.atlassian.bamboo.specs.api.builders.pbc.ContainerSize;
import com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForJob;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.dependencies.Dependencies;
import com.atlassian.bamboo.specs.api.builders.plan.dependencies.DependenciesConfiguration;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepositoryIdentifier;
import com.atlassian.bamboo.specs.api.builders.trigger.AnyTriggerCondition;
import com.atlassian.bamboo.specs.builders.task.CheckoutItem;
import com.atlassian.bamboo.specs.builders.task.ScriptTask;
import com.atlassian.bamboo.specs.builders.task.VcsCheckoutTask;
import com.atlassian.bamboo.specs.builders.trigger.BitbucketServerTrigger;
import com.atlassian.bamboo.specs.util.BambooServer;
import com.atlassian.bamboo.specs.util.MapBuilder;

import java.nio.file.Paths;

@BambooSpec
public class IsolatedDockerSpec {

    public Plan plan() {
        return new Plan(
                        new Project()
                                .key(new BambooKey("UPGRADE"))
                                .name("Smoke Tests - Bamboo Upgrade Prerequisites")
                                .description("Bamboo Upgrade Prerequisites"),
                        "PBC build against current SBAC",
                        new BambooKey("PBCB"))
                .pluginConfigurations(new PlanOwner("mknight"))
                .stages(new Stage("Default Stage")
                        .jobs(new Job("Default Job", new BambooKey("JOB1"))
                                .pluginConfigurations(new PerBuildContainerForJob()
                                        .image("docker.atl-paas.net/buildeng/agent-baseagent:staging")
                                        .size(ContainerSize.REGULAR))
                                .tasks(
                                        new VcsCheckoutTask()
                                                .description("Checkout Default Repository")
                                                .checkoutItems(new CheckoutItem()
                                                        .repository(new VcsRepositoryIdentifier()
                                                                .name("bamboo-isolated-docker"))),
                                        new ScriptTask()
                                                .inlineBodyFromPath(
                                                        Paths.get("src/main/resources/check-bamboo-version.sh")))))
                .linkedRepositories("bamboo-isolated-docker")
                .planBranchManagement(
                        new PlanBranchManagement().delete(new BranchCleanup()).notificationForCommitters())
                .dependencies(
                        new Dependencies().configuration(new DependenciesConfiguration().enabledForBranches(false)))
                .forceStopHungBuilds();
    }

    public PlanPermissions planPermission() {
        return new PlanPermissions(new PlanIdentifier("UPGRADE", "PBCB"))
                .permissions(new Permissions()
                        .loggedInUserPermissions(PermissionType.VIEW)
                        .anonymousUserPermissionView());
    }

    public static void main(String... argv) {
        // By default credentials are read from the '.credentials' file.
        BambooServer bambooServer = new BambooServer("https://staging-bamboo.internal.atlassian.com");
        final IsolatedDockerSpec isolatedDockerSpec = new IsolatedDockerSpec();

        final Plan plan = isolatedDockerSpec.plan();
        bambooServer.publish(plan);

        final PlanPermissions planPermission = isolatedDockerSpec.planPermission();
        bambooServer.publish(planPermission);
    }
}
