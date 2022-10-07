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

import com.atlassian.bamboo.pageobjects.elements.AuiDropDown2Menu;
import com.atlassian.bamboo.pageobjects.pages.deployment.ViewDeploymentProjectPage;
import com.atlassian.pageobjects.elements.ElementBy;
import com.atlassian.pageobjects.elements.PageElementFinder;
import javax.inject.Inject;
import org.openqa.selenium.By;

public class CustomViewDeploymentProjectPage extends ViewDeploymentProjectPage {
    @ElementBy(id = "configure-project-actions")
    private AuiDropDown2Menu actionsMenu;

    @Inject
    private PageElementFinder elementFinder;

    public CustomViewDeploymentProjectPage(long deploymentProjectId) {
        super(deploymentProjectId);
    }

    public boolean isViewIamSubjectLinkVisible() {
        actionsMenu.open();
        return !elementFinder.findAll(By.id("subject-id-button")).isEmpty();
    }
}
