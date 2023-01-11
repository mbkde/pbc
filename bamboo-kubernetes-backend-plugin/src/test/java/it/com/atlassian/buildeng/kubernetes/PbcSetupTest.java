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

import com.atlassian.bamboo.testutils.user.TestUser;
import com.atlassian.buildeng.kubernetes.pageobject.GenericKubernetesConfigPage;
import com.atlassian.buildeng.kubernetes.pageobject.GenericPerBuildContainerConfigurationPage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
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

    @Test
    public void testPbcConfiguration() throws Exception {
        bamboo.fastLogin(TestUser.ADMIN);

        backdoor.agents().enableRemoteAgents(true);

        bamboo
                .visit(GenericPerBuildContainerConfigurationPage.class)
                .setEnableSwitch(true)
                .setAgentCreationThrottling(100)
                .setArchitectureConfig("")
                .save();
        bamboo
                .visit(GenericKubernetesConfigPage.class)
                .setSidekickImage(SIDEKICK_IMAGE)
                .setCurrentContext("")
                .setPodTemplate(getPodTemplateAsString())
                .setArchitecturePodConfig("")
                .save();
    }

    private String getPodTemplateAsString() throws IOException {
        return FileUtils.readFileToString(new File(Objects
                .requireNonNull(this.getClass().getResource("/basePodTemplate.yaml"))
                .getFile()), "UTF-8");
    }
}
