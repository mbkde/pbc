/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.BandanaManager;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public class GlobalConfiguration {

    static String BANDANA_SIDEKICK_KEY = "com.atlassian.buildeng.pbc.kubernetes.sidekick";
    static String BANDANA_POD_TEMPLATE = "com.atlassian.buildeng.pbc.kubernetes.podtemplate";
    static String BANDANA_POD_LOGS_URL = "com.atlassian.buildeng.pbc.kubernetes.podlogurl";

    private final BandanaManager bandanaManager;
    private final AdministrationConfigurationAccessor admConfAccessor;

    public GlobalConfiguration(BandanaManager bandanaManager,
                               AdministrationConfigurationAccessor admConfAccessor) {
        this.bandanaManager = bandanaManager;
        this.admConfAccessor = admConfAccessor;
    }

    public String getBambooBaseUrl() {
        return admConfAccessor.getAdministrationConfiguration().getBaseUrl();
    }
    
    /**
     * Strips characters from bamboo baseurl to conform to kubernetes label values.
     * @return value conforming to kube label value constraints.
     */
    public String getBambooBaseUrlAskKubeLabel() {
        return stripLabelValue(getBambooBaseUrl());
    }
    
    static String stripLabelValue(String value) {
        return value.replaceAll("[^A-Za-z0-9_\\-\\.]", "");
    }

    public synchronized String getCurrentSidekick() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY);
    }

    /**
     * Returns the template yaml file that encodes the server/cluster specific parts of pod definition.
     * @return string representation of yaml
     */
    public synchronized String getPodTemplateAsString() {
        String template = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_POD_TEMPLATE);
        if (template == null) {
            return "apiVersion: v1\n"
                  + "kind: Pod";
        }
        return template;
    }
    
    public synchronized String getPodLogsUrl() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_POD_LOGS_URL);
    }

    /**
     * Saves changes to the configuration.
     */
    public synchronized void persist(String sidekick, String podTemplate, String podLogUrl) {
        Preconditions.checkArgument(StringUtils.isNotBlank(sidekick));
        Preconditions.checkArgument(StringUtils.isNotBlank(podTemplate));
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY, sidekick);
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_TEMPLATE, podTemplate);
        if (StringUtils.isBlank(podLogUrl)) {
            bandanaManager.removeValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_LOGS_URL);
        } else {
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_LOGS_URL, podLogUrl);
        }
    }

}
