/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package it.com.atlassian.buildeng.kubernetes;

import static com.atlassian.pageobjects.elements.query.Conditions.forSupplier;
import static com.atlassian.pageobjects.elements.query.Poller.waitUntilTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.specs.api.builders.deployment.Deployment;
import com.atlassian.bamboo.specs.api.builders.deployment.Environment;
import com.atlassian.bamboo.specs.api.builders.deployment.ReleaseNaming;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.testutils.UniqueNameHelper;
import com.atlassian.bamboo.testutils.specs.TestPlanSpecsHelper;
import com.atlassian.bamboo.testutils.user.TestUser;
import com.atlassian.buildeng.kubernetes.pageobject.CustomPlanSummaryPage;
import com.atlassian.buildeng.kubernetes.pageobject.CustomViewDeploymentProjectPage;
import com.atlassian.buildeng.kubernetes.pageobject.GenericKubernetesConfigPage;
import com.atlassian.buildeng.kubernetes.pageobject.GenericPerBuildContainerConfigurationPage;
import com.atlassian.buildeng.kubernetes.pageobject.PerBuildContainerConfigPage;
import com.atlassian.pageobjects.elements.timeout.TimeoutType;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

/**
 * Tests UI elements are visible or hidden depending on vendor choice.
 */
public class AwsVendorTest extends AbstractPbcTest {
    @Test
    public void awsVendorMakesUIElementsVisible() throws Exception {
        bamboo.fastLogin(TestUser.ADMIN);
        backdoor.troubleshooting().dismissLocalAgentNotification();
        backdoor.troubleshooting().dismissNotification(2, TestUser.ADMIN_NAME); // dismiss H2 warning
        updateGenericPerBuildContainerConfigurationPage(true, true);

        GenericKubernetesConfigPage k8sGenericConfigPage = bamboo.visit(GenericKubernetesConfigPage.class);
        k8sGenericConfigPage.setSidekickImage("docker.atl-paas.net/sox/buildeng/bamboo-agent-sidekick:latest");
        assertTrue(k8sGenericConfigPage.isIamRequestTemplateVisible());
        assertTrue(k8sGenericConfigPage.isIamSubjectIdPrefixVisible());
        k8sGenericConfigPage.setIamRequestTemplate("request");
        k8sGenericConfigPage.setIamSubjectIdPrefix("subject");
        k8sGenericConfigPage.save();

        PlanKey planKey = assertPlanSpecificAwsControlsVisible(true);
        assertDeploymentSpecificAwsControlsVisible(planKey, true);
    }

    @Test
    public void someElementsAreNotVisibleWhenNonAwsVendorIsChosen() throws Exception {
        bamboo.fastLogin(TestUser.ADMIN);
        backdoor.troubleshooting().dismissLocalAgentNotification();
        backdoor.troubleshooting().dismissNotification(2, TestUser.ADMIN_NAME);  // dismiss H2 warning
        updateGenericPerBuildContainerConfigurationPage(false, true);

        GenericKubernetesConfigPage k8sGenericConfigPage = bamboo.visit(GenericKubernetesConfigPage.class);
        k8sGenericConfigPage.setSidekickImage("docker.atl-paas.net/sox/buildeng/bamboo-agent-sidekick:latest");
        // confirm some elements are hidden when AWS vendor is not chosen
        assertFalse(k8sGenericConfigPage.isIamRequestTemplateVisible());
        assertFalse(k8sGenericConfigPage.isIamSubjectIdPrefixVisible());
        k8sGenericConfigPage.save();

        PlanKey planKey = assertPlanSpecificAwsControlsVisible(false);
        assertDeploymentSpecificAwsControlsVisible(planKey, false);
    }

    @Test
    public void allElementsNotVisibleWhenPluginDisabled() throws Exception {
        bamboo.fastLogin(TestUser.ADMIN);
        backdoor.troubleshooting().dismissLocalAgentNotification();
        backdoor.troubleshooting().dismissNotification(2, TestUser.ADMIN_NAME);  // dismiss H2 warning
        updateGenericPerBuildContainerConfigurationPage(false, false);

        GenericKubernetesConfigPage k8sGenericConfigPage = bamboo.visit(GenericKubernetesConfigPage.class);
        k8sGenericConfigPage.setSidekickImage("docker.atl-paas.net/sox/buildeng/bamboo-agent-sidekick:latest");
        assertFalse(k8sGenericConfigPage.isIamRequestTemplateVisible());
        assertFalse(k8sGenericConfigPage.isIamSubjectIdPrefixVisible());
        k8sGenericConfigPage.save();

        assertNothingIsVisible();
    }

    private void assertNothingIsVisible() throws Exception {
        String projectKey = UniqueNameHelper.makeUniqueName("PROJ");
        String planKeyStr = UniqueNameHelper.makeUniqueName("PLAN");
        String jobKey = "JOB1";

        Plan plan = new Plan(new Project().key(projectKey).name(projectKey), planKeyStr, planKeyStr).stages(new Stage(
                "Default stage").jobs(new Job("Default job", jobKey)));
        final PlanKey planKey = TestPlanSpecsHelper.getPlanKey(plan);
        backdoor.plans().createPlan(plan);
        PerBuildContainerConfigPage dockerConfigPage =
                bamboo.visit(PerBuildContainerConfigPage.class, PlanKeys.getPlanKey(projectKey, planKeyStr, jobKey));
        dockerConfigPage.choosePerBuildContainerPlugin();

        assertThat(dockerConfigPage.isAwsIamRoleVisible(), CoreMatchers.is(false));
        assertThat(dockerConfigPage.isDockerImagePresent(), CoreMatchers.is(false));

        CustomPlanSummaryPage summaryPage = bamboo.visit(CustomPlanSummaryPage.class, planKey);
        assertThat(summaryPage.isViewIamSubjectLinkVisible(), CoreMatchers.is(false));
    }

    private void assertDeploymentSpecificAwsControlsVisible(PlanKey planKey, boolean isVisible) throws Exception {
        Deployment deployment = new Deployment(new PlanIdentifier(PlanKeys.getProjectKeyPart(planKey),
                PlanKeys.getPlanKeyPart(planKey)), UniqueNameHelper.makeUniqueName("DEPL"))
                .releaseNaming(new ReleaseNaming("version-1"))
                .environments(new Environment(UniqueNameHelper.makeUniqueName("ENV")));
        long deploymentId = backdoor.deployments().createOrUpdateDeploymentProject(deployment);

        CustomViewDeploymentProjectPage viewDeploymentProject =
                bamboo.visit(CustomViewDeploymentProjectPage.class, deploymentId);
        assertThat(viewDeploymentProject.isViewIamSubjectLinkVisible(), CoreMatchers.is(isVisible));
    }

    private PlanKey assertPlanSpecificAwsControlsVisible(boolean isVisible) throws Exception {
        String projectKey = UniqueNameHelper.makeUniqueName("PROJ");
        String planKeyStr = UniqueNameHelper.makeUniqueName("PLAN");
        String jobKey = "JOB1";

        Plan plan = new Plan(new Project().key(projectKey).name(projectKey), planKeyStr, planKeyStr).stages(new Stage(
                "Default stage").jobs(new Job("Default job", jobKey)));
        final PlanKey planKey = TestPlanSpecsHelper.getPlanKey(plan);
        backdoor.plans().createPlan(plan);
        PerBuildContainerConfigPage dockerConfigPage =
                bamboo.visit(PerBuildContainerConfigPage.class, PlanKeys.getPlanKey(projectKey, planKeyStr, jobKey));
        dockerConfigPage.choosePerBuildContainerPlugin();

        assertThat(dockerConfigPage.isAwsIamRoleVisible(), CoreMatchers.is(isVisible));

        CustomPlanSummaryPage summaryPage = bamboo.visit(CustomPlanSummaryPage.class, planKey);
        assertThat(summaryPage.isViewIamSubjectLinkVisible(), CoreMatchers.is(isVisible));
        return planKey;
    }

    private void updateGenericPerBuildContainerConfigurationPage(boolean awsVendor, boolean enabled) {
        GenericPerBuildContainerConfigurationPage genericConfigPage =
                bamboo.visit(GenericPerBuildContainerConfigurationPage.class);
        genericConfigPage.awsVendor(awsVendor);
        genericConfigPage.setDefaultImage("docker.atl-paas.net/sox/buildeng/agent-baseagent");
        genericConfigPage.setAgentCreationThrottling(10);
        genericConfigPage.setEnableSwitch(enabled);
        genericConfigPage.save();
        waitUntilTrue(forSupplier(timeouts.timeoutFor(TimeoutType.SLOW_PAGE_LOAD), genericConfigPage::isSaved));
    }
}
