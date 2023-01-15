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

package com.atlassian.buildeng.kubernetes.pageobject;

import static com.atlassian.pageobjects.elements.query.Conditions.forSupplier;
import static com.atlassian.pageobjects.elements.query.Poller.waitUntilTrue;
import static org.junit.Assert.assertTrue;

import com.atlassian.aui.auipageobjects.AuiCheckbox;
import com.atlassian.bamboo.pageobjects.pages.plan.configuration.ConfigureJobDockerPage;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.pageobjects.elements.ElementBy;
import com.atlassian.pageobjects.elements.Option;
import com.atlassian.pageobjects.elements.PageElement;
import com.atlassian.pageobjects.elements.PageElementFinder;
import com.atlassian.pageobjects.elements.SelectElement;
import com.atlassian.pageobjects.elements.timeout.TimeoutType;
import java.util.Optional;
import javax.inject.Inject;
import org.openqa.selenium.By;

public class PerBuildContainerConfigPage extends ConfigureJobDockerPage {
    private static final String ISOLATED_DOCKER_AWS_ROLE = "configureDocker_custom_isolated_docker_awsRole";

    @ElementBy(id = "isolationTypePBC")
    private AuiCheckbox choosePbc;

    @ElementBy(id = "configureDocker_custom_isolated_docker_image")
    private PageElement dockerImageInput;

    @ElementBy(id = ISOLATED_DOCKER_AWS_ROLE)
    private PageElement awsIamRole;

    @ElementBy(id = "configureDocker_custom_isolated_docker_imageSize")
    private SelectElement agentSizeSelect;

    @Inject
    private PageElementFinder pageElementFinder;

    public PerBuildContainerConfigPage(PlanKey planKey) {
        super(planKey);
    }

    public boolean isDockerImageVisible() {
        return dockerImageInput.isVisible();
    }

    public boolean isDockerImagePresent() {
        return dockerImageInput.isPresent();
    }

    public boolean eitherDockerImageVisibleOrWarning() {
        return (isDockerImagePresent() && isDockerImageVisible())
                || !pageElementFinder
                        .findAll(By.className("aui-message-warning"))
                        .isEmpty();
    }

    public boolean isAwsIamRoleVisible() {
        return !pageElementFinder.findAll(By.id(ISOLATED_DOCKER_AWS_ROLE)).isEmpty();
    }

    public PerBuildContainerConfigPage choosePerBuildContainerPlugin() {
        choosePbc.click();
        waitUntilTrue(
                forSupplier(timeouts.timeoutFor(TimeoutType.SLOW_PAGE_LOAD), this::eitherDockerImageVisibleOrWarning));
        return this;
    }

    public PerBuildContainerConfigPage setDockerImage(final String dockerImage) {
        dockerImageInput.clear().type(dockerImage);
        return this;
    }

    public PerBuildContainerConfigPage selectAgentSize(final String sizeName) {
        Optional<Option> agentSizeOption = agentSizeSelect.getAllOptions().stream()
                .filter(size -> size.text().startsWith(sizeName))
                .findFirst();
        assertTrue(agentSizeOption.isPresent());
        agentSizeSelect.select(agentSizeOption.get());

        return this;
    }
}
