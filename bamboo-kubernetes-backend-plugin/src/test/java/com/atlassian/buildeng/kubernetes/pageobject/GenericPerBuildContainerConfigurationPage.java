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

import com.atlassian.aui.auipageobjects.AuiCheckbox;
import com.atlassian.bamboo.pageobjects.pages.AbstractBambooPage;
import com.atlassian.pageobjects.elements.ElementBy;
import com.atlassian.pageobjects.elements.PageElement;
import com.atlassian.pageobjects.elements.query.TimedCondition;

public class GenericPerBuildContainerConfigurationPage extends AbstractBambooPage {
    @ElementBy(id = "enableSwitch")
    private PageElement enableSwitch;
    @ElementBy(id = "setRemoteConfig_awsVendor")
    private AuiCheckbox awsVendor;
    @ElementBy(id = "setRemoteConfig_defaultImage")
    private PageElement defaultImage;
    @ElementBy(id = "setRemoteConfig_maxAgentCreationPerMinute")
    private PageElement maxAgentCreationPerMinute;
    @ElementBy(id = "setRemoteConfig_architectureConfig")
    private PageElement architectureConfig;
    @ElementBy(id = "setRemoteConfig_save")
    private PageElement saveButton;
    @ElementBy(id = "errorMessage")
    private PageElement errorMessage;
    @ElementBy(cssSelector = ".save-status")
    private PageElement saveStatus;
    @ElementBy(id="load_complete")
    private PageElement loadComplete;

    @Override
    public PageElement indicator() {
        return defaultImage;
    }

    @Override
    protected TimedCondition isPageLoaded() {
        return loadComplete.timed().hasValue("true");
    }

    @Override
    public String getUrl() {
        return "/admin/viewIsolatedDockerConfiguration.action";
    }

    public GenericPerBuildContainerConfigurationPage setEnableSwitch(boolean enabled) {
        final String checked = this.enableSwitch.asWebElement().getAttribute("checked");
        if (enabled && (checked == null || Boolean.valueOf(checked).equals(Boolean.FALSE))) {
            this.enableSwitch.click();
        } else if (!enabled && Boolean.valueOf(checked).equals(Boolean.TRUE)) {
            this.enableSwitch.click();
        }
        return this;
    }

    public GenericPerBuildContainerConfigurationPage awsVendor(boolean awsVendor) {
        if(awsVendor) {
            this.awsVendor.check();
        } else {
            this.awsVendor.uncheck();
        }
        return this;
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
