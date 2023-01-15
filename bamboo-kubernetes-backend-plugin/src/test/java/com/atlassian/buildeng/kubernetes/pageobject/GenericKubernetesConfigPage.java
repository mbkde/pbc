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
import com.atlassian.pageobjects.elements.PageElementFinder;
import com.atlassian.pageobjects.elements.query.TimedCondition;
import javax.inject.Inject;
import org.openqa.selenium.By;

public class GenericKubernetesConfigPage extends AbstractBambooPage {
    public static final String IAM_REQUEST_TEMPLATE = "setRemoteConfig_iamRequestTemplate";
    public static final String IAM_SUBJECT_ID_PREFIX = "setRemoteConfig_iamSubjectIdPrefix";

    @ElementBy(id = "setRemoteConfig_sidekickToUse")
    private PageElement sidekickImage;

    @ElementBy(id = "setRemoteConfig_currentContext")
    private PageElement currentContext;

    @ElementBy(id = "setRemoteConfig_useClusterRegistry")
    private AuiCheckbox useClusterRegistry;

    @ElementBy(id = "setRemoteConfig_clusterRegistryAvailableSelector")
    private PageElement availableClusterLabel;

    @ElementBy(id = "setRemoteConfig_clusterRegistryPrimarySelector")
    private PageElement clusterRegistryPrimarySelector;

    @ElementBy(id = "setRemoteConfig_podTemplate")
    private PageElement podTemplate;

    @ElementBy(id = "setRemoteConfig_architecturePodConfig")
    private PageElement architecturePodConfig;

    @ElementBy(id = IAM_REQUEST_TEMPLATE)
    private PageElement iamRequestTemplate;

    @ElementBy(id = IAM_SUBJECT_ID_PREFIX)
    private PageElement iamSubjectIdPrefix;

    @ElementBy(id = "setRemoteConfig_containerSizes")
    private PageElement containerSizes;

    @ElementBy(id = "errorMessage")
    private PageElement errorMessage;

    @ElementBy(cssSelector = ".save-status")
    private PageElement saveStatus;

    @ElementBy(id = "setRemoteConfig_save")
    private PageElement saveButton;

    @ElementBy(id = "load_complete")
    private PageElement loadComplete;

    @Inject
    private PageElementFinder elementFinder;

    @Override
    public PageElement indicator() {
        return sidekickImage;
    }

    @Override
    public String getUrl() {
        return "/admin/viewKubernetesConfiguration.action";
    }

    @Override
    protected TimedCondition isPageLoaded() {
        return loadComplete.timed().hasValue("true");
    }

    public GenericKubernetesConfigPage setSidekickImage(String sidekickImage) {
        this.sidekickImage.clear().type(sidekickImage);
        return this;
    }

    public GenericKubernetesConfigPage setCurrentContext(String currentContext) {
        this.currentContext.clear().type(currentContext);
        return this;
    }

    public GenericKubernetesConfigPage useClusterRegistry(boolean use) {
        if (use) {
            this.useClusterRegistry.check();
        } else {
            this.useClusterRegistry.uncheck();
        }
        return this;
    }

    public GenericKubernetesConfigPage setAvailableClusterLabel(String availableClusterLabel) {
        this.availableClusterLabel.clear().type(availableClusterLabel);
        return this;
    }

    public GenericKubernetesConfigPage setClusterRegistryPrimarySelector(String clusterRegistryPrimarySelector) {
        this.clusterRegistryPrimarySelector.clear().type(clusterRegistryPrimarySelector);
        return this;
    }

    public GenericKubernetesConfigPage setPodTemplate(String podTemplate) {
        this.podTemplate.clear().type(podTemplate);
        return this;
    }

    public GenericKubernetesConfigPage setArchitecturePodConfig(String architecturePodConfig) {
        this.architecturePodConfig.clear().type(architecturePodConfig);
        return this;
    }

    public GenericKubernetesConfigPage setIamRequestTemplate(String iamRequestTemplate) {
        this.iamRequestTemplate.clear().type(iamRequestTemplate);
        return this;
    }

    public boolean isIamRequestTemplateVisible() {
        return !elementFinder.findAll(By.id(IAM_REQUEST_TEMPLATE)).isEmpty();
    }

    public GenericKubernetesConfigPage setIamSubjectIdPrefix(String iamSubjectIdPrefix) {
        this.iamSubjectIdPrefix.clear().type(iamSubjectIdPrefix);
        return this;
    }

    public boolean isIamSubjectIdPrefixVisible() {
        return !elementFinder.findAll(By.id(IAM_SUBJECT_ID_PREFIX)).isEmpty();
    }

    public GenericKubernetesConfigPage setContainerSizes(String containerSizes) {
        this.containerSizes.clear().type(containerSizes);
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
