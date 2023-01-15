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
import com.atlassian.buildeng.kubernetes.pageobject.PerBuildContainerConfigPage;
import com.atlassian.pageobjects.elements.query.Poller;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class SimplePbcBuildTest extends AbstractPbcTest {
    /**
     * Use longer timeout for build, so a PBC agent have enough time to start
     */
    private static final Poller.WaitTimeout PBC_BUILD_WAIT_TIMEOUT = Poller.by(10, TimeUnit.MINUTES);

    private static final String PYTHON_DOCKER_IMAGE = "python:3.10-slim-bullseye";

    @Test
    public void testPbcBuild() throws Exception {
        bamboo.fastLogin(TestUser.ADMIN);

        final Job pythonJob = TestPlanSpecsHelper.defaultJob();
        pythonJob.tasks(new ScriptTask().inlineBody("set -eux\npython --version"));

        final Plan plan = TestPlanSpecsHelper.singleJobPlan(pythonJob);
        final PlanKey planKey = TestPlanSpecsHelper.getPlanKey(plan);
        final PlanProperties planProperties = backdoor.plans().createPlan(plan);

        final PlanKey defaultJobKey = TestPlanSpecsHelper.getDefaultJobKey(planProperties);
        bamboo.visit(PerBuildContainerConfigPage.class, defaultJobKey)
                .choosePerBuildContainerPlugin()
                .setDockerImage(PYTHON_DOCKER_IMAGE)
                .selectAgentSize("Extra Small")
                .saveExpectingSuccess();

        triggerBuildAndAwaitSuccess(planKey);
    }

    private void triggerBuildAndAwaitSuccess(PlanKey planKey) {
        final RestQueuedBuild queuedBuildResponse =
                backdoor.plans().triggerBuildWithResponse(planKey, Collections.emptyMap());
        final PlanResultKey planResultKey = PlanKeys.getPlanResultKey(queuedBuildResponse.getBuildResultKey());
        backdoor.plans().waitForSuccessfulBuild(planResultKey, PBC_BUILD_WAIT_TIMEOUT);
    }
}
