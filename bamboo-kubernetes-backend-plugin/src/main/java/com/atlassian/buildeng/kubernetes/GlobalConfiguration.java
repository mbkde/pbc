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
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.google.common.base.Preconditions;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

public class GlobalConfiguration {

    static String BANDANA_SIDEKICK_KEY = "com.atlassian.buildeng.pbc.kubernetes.sidekick";
    static String BANDANA_POD_TEMPLATE = "com.atlassian.buildeng.pbc.kubernetes.podtemplate";
    static String BANDANA_POD_LOGS_URL = "com.atlassian.buildeng.pbc.kubernetes.podlogurl";
    static String BANDANA_CURRENT_CONTEXT = "com.atlassian.buildeng.pbc.kubernetes.context";

    private final BandanaManager bandanaManager;
    private final AdministrationConfigurationAccessor admConfAccessor;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    public GlobalConfiguration(BandanaManager bandanaManager, AuditLogService auditLogService,
                               AdministrationConfigurationAccessor admConfAccessor, 
                               BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.admConfAccessor = admConfAccessor;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
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
        return value.replaceAll("[^-A-Za-z0-9_.]", "");
    }

    public String getCurrentSidekick() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY);
    }

    public String getCurrentContext() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CURRENT_CONTEXT);
    }

    /**
     * Returns the template yaml file that encodes the server/cluster specific parts of pod definition.
     * @return string representation of yaml
     */
    public String getPodTemplateAsString() {
        String template = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_POD_TEMPLATE);
        if (template == null) {
            return "apiVersion: v1\n"
                  + "kind: Pod";
        }
        return template;
    }
    
    public String getPodLogsUrl() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_POD_LOGS_URL);
    }

    /**
     * Saves changes to the configuration.
     */
    public void persist(String sidekick, String currentContext, String podTemplate, String podLogUrl) {
        Preconditions.checkArgument(StringUtils.isNotBlank(sidekick));
        Preconditions.checkArgument(StringUtils.isNotBlank(currentContext));
        Preconditions.checkArgument(StringUtils.isNotBlank(podTemplate));
        if (!StringUtils.equals(sidekick, getCurrentSidekick())) {
            auditLogEntry("PBC Sidekick Image", getCurrentSidekick(), sidekick);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY, sidekick);
        }
        if (!StringUtils.equals(podTemplate, getPodTemplateAsString())) {
            auditLogEntry("PBC Kubernetes Pod Template", getPodTemplateAsString(), podTemplate);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_TEMPLATE, podTemplate);
        }
        if (!StringUtils.equals(podLogUrl, getPodLogsUrl())) {
            auditLogEntry("PBC Kubernetes Container Logs URL", getPodLogsUrl(), podLogUrl);
            if (StringUtils.isBlank(podLogUrl)) {
                bandanaManager.removeValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_LOGS_URL);
            } else {
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_LOGS_URL, podLogUrl);
            }
        }
        persistCurrentContext(currentContext);
    }

    /**
     * There is a REST endpoint to specifically update current context as this needs to be done automatically across
     * multiple Bamboo servers.
     */
    public void persistCurrentContext(String currentContext) {
        Preconditions.checkArgument(StringUtils.isNotBlank(currentContext));
        if (!StringUtils.equals(currentContext, getCurrentContext())) {
            auditLogEntry("PBC Kubernetes Current Context", getCurrentContext(), currentContext);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CURRENT_CONTEXT, currentContext);
        }
    }

    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new  AuditLogMessage(authenticationContext.getUserName(), new Date(), null, null, 
                AuditLogEntry.TYPE_FIELD_CHANGE, name, oldValue, newValue);
        auditLogService.log(ent);
    }
    
}
