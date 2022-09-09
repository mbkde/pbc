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

import com.atlassian.bamboo.pageobjects.pages.AbstractBambooPage;
import com.atlassian.pageobjects.elements.ElementBy;
import com.atlassian.pageobjects.elements.PageElement;
import com.atlassian.pageobjects.elements.query.TimedCondition;

public class GenericPerBuildContainerConfigurationPage extends AbstractBambooPage {
    @ElementBy(id = "defaultImage")
    private PageElement defaultImage;
    @ElementBy(id = "maxAgentCreationPerMinute")
    private PageElement maxAgentCreationPerMinute;
    @ElementBy(id = "architectureConfig")
    private PageElement architectureConfig;
    @ElementBy(xpath = "//form[@id='setRemoteConfig']//button[text()='Save']")
    private PageElement saveButton;
    @ElementBy(id = "errorMessage")
    private PageElement errorMessage;
    @ElementBy(cssSelector = ".save-status")
    private PageElement saveStatus;

    @Override
    public PageElement indicator() {
        return defaultImage;
    }

    @Override
    protected TimedCondition isPageLoaded() {
        return saveButton.timed().isEnabled();
    }

    @Override
    public String getUrl() {
        return "/admin/viewIsolatedDockerConfiguration.action";
    }

    public GenericPerBuildContainerConfigurationPage setDefaultImage(String defaultImage) {
        this.defaultImage.clear().type(defaultImage);
        return this;
    }

    public GenericPerBuildContainerConfigurationPage setAgentCreationThrottling(int maxAgentsPerMinute) {
        maxAgentCreationPerMinute.clear().type(String.valueOf(maxAgentsPerMinute));
        return this;
    }

    public GenericPerBuildContainerConfigurationPage setArchitectureConfig(String architectureConfig) {
        this.architectureConfig.clear().type(architectureConfig);
        return this;
    }

    public void save() {
        saveButton.click();
    }

    public String getErrors() {
        return errorMessage.getText();
    }

    public String getSaveStatus() {
        return saveStatus.getText();
    }

    public boolean isSaved() {
        return "Saved".equals(getSaveStatus());
    }
}
