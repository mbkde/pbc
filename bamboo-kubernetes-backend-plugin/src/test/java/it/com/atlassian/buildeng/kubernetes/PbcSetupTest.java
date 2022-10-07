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

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.model.plan.PlanProperties;
import com.atlassian.bamboo.specs.builders.task.ScriptTask;
import com.atlassian.bamboo.testutils.backdoor.model.queue.RestQueuedBuild;
import com.atlassian.bamboo.testutils.specs.TestPlanSpecsHelper;
import com.atlassian.bamboo.testutils.user.TestUser;
import com.atlassian.buildeng.kubernetes.pageobject.GenericKubernetesConfigPage;
import com.atlassian.buildeng.kubernetes.pageobject.GenericPerBuildContainerConfigurationPage;
import com.atlassian.buildeng.kubernetes.pageobject.PerBuildContainerConfigPage;
import com.atlassian.pageobjects.elements.query.Poller;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class PbcSetupTest extends AbstractPbcTest {

    /**
     * Currently we used a hacked sidekick image that can update the agent during startup
     * The image can be found on branch: BDEV-16966-sidekick-for-integration-tests
     * <p>
     * Should be removed/replaced when we decide what to do with sidekick in PBC
     */
    private static final String SIDEKICK_IMAGE = "docker.atl-paas.net/bamboo/bamboo-agent-sidekick:integration-tests";

    /**
     * Use longer timeout for build, so a PBC agent have enough time to start
     */
    private static final Poller.WaitTimeout PBC_BUILD_WAIT_TIMEOUT = Poller.by(10, TimeUnit.MINUTES);

    @Test
    public void testPbcConfiguration() throws Exception {
        bamboo.fastLogin(TestUser.ADMIN);

        backdoor.agents().enableRemoteAgents(true);

        bamboo.visit(GenericPerBuildContainerConfigurationPage.class)
                .setEnableSwitch(true)
                .setAgentCreationThrottling(100)
                .setArchitectureConfig("")
                .save();
        bamboo.visit(GenericKubernetesConfigPage.class)
                .setSidekickImage(SIDEKICK_IMAGE)
                .setCurrentContext("")
                .setPodTemplate(getPodTemplateAsString())
                .setArchitecturePodConfig("")
                .save();

        // TODO jmajkutewicz: move this to a different class:
        final String pythonImage = "python:3.10-slim-bullseye";
        bamboo.fastLogin(TestUser.ADMIN);

        final Job pythonJob = TestPlanSpecsHelper.defaultJob();
        pythonJob.tasks(new ScriptTask().inlineBody("set -eux\npython --version"));

        final Plan plan = TestPlanSpecsHelper.singleJobPlan(pythonJob);
        final PlanKey planKey = TestPlanSpecsHelper.getPlanKey(plan);
        final PlanProperties planProperties = backdoor.plans().createPlan(plan);

        final PlanKey defaultJobKey = TestPlanSpecsHelper.getDefaultJobKey(planProperties);
        bamboo.visit(PerBuildContainerConfigPage.class, defaultJobKey)
                .choosePerBuildContainerPlugin()
                .setDockerImage(pythonImage)
                .selectAgentSize("Extra Small")
                .saveExpectingSuccess();

        triggerBuildAndAwaitSuccess(planKey);
    }

    private void triggerBuildAndAwaitSuccess(PlanKey planKey) {
        final RestQueuedBuild queuedBuildResponse = backdoor.plans().triggerBuildWithResponse(planKey, Collections.emptyMap());
        final PlanResultKey planResultKey = PlanKeys.getPlanResultKey(queuedBuildResponse.getBuildResultKey());
        backdoor.plans().waitForSuccessfulBuild(planResultKey, PBC_BUILD_WAIT_TIMEOUT);
    }

    private String getPodTemplateAsString() throws IOException {
        return FileUtils.readFileToString(new File(Objects.requireNonNull(this.getClass()
                .getResource("/basePodTemplate.yaml")).getFile()), "UTF-8");
    }
}
