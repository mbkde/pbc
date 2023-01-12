/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.ecs.remote;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.remote.rest.Config;
import com.google.common.base.Preconditions;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

public class GlobalConfiguration {

    static String BANDANA_AWS_ROLE_KEY = "com.atlassian.buildeng.ecs.remote.awsrole";
    static String BANDANA_SIDEKICK_KEY = "com.atlassian.buildeng.ecs.remote.sidekick";
    static String BANDANA_SERVER_URL_KEY = "com.atlassian.buildeng.ecs.remote.server";
    static String BANDANA_PREEMPTIVE_KEY = "com.atlassian.buildeng.ecs.remote.preemptive";

    private final BandanaManager bandanaManager;
    private final AdministrationConfigurationAccessor admConfAccessor;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    public GlobalConfiguration(
            BandanaManager bandanaManager,
            AdministrationConfigurationAccessor admConfAccessor,
            AuditLogService auditLogService,
            BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.admConfAccessor = admConfAccessor;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }

    public String getBambooBaseUrl() {
        return admConfAccessor.getAdministrationConfiguration().getBaseUrl();
    }

    public String getCurrentSidekick() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY);
    }

    public String getCurrentRole() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AWS_ROLE_KEY);
    }

    public String getCurrentServer() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SERVER_URL_KEY);
    }

    public void persist(Config config) {
        Preconditions.checkArgument(StringUtils.isNotBlank(config.getServerUrl()));
        if (!StringUtils.equals(config.getAwsRole(), getCurrentRole())) {
            auditLogEntry("PBC AWS Role", getCurrentRole(), config.getAwsRole());
            if (StringUtils.isBlank(config.getAwsRole())) {
                bandanaManager.removeValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AWS_ROLE_KEY);
            } else {
                bandanaManager.setValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AWS_ROLE_KEY, config.getAwsRole());
            }
        }
        if (!StringUtils.equals(config.getSidekickImage(), getCurrentSidekick())) {
            auditLogEntry("PBC Sidekick Image", getCurrentSidekick(), config.getSidekickImage());
            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY, config.getSidekickImage());
        }
        if (!StringUtils.equals(config.getServerUrl(), getCurrentServer())) {
            auditLogEntry("PBC Remote Service URL", getCurrentServer(), config.getServerUrl());
            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SERVER_URL_KEY, config.getServerUrl());
        }
        boolean newPremptive = config.isPreemptiveScaling();
        if (isPreemptiveScaling() != newPremptive) {
            auditLogEntry("PBC Preemptive scaling", "" + isPreemptiveScaling(), "" + newPremptive);
            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_PREEMPTIVE_KEY, config.isPreemptiveScaling());
        }
    }

    public boolean isPreemptiveScaling() {
        Boolean val = (Boolean) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_PREEMPTIVE_KEY);
        return val != null ? val : false;
    }

    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new AuditLogMessage(
                authenticationContext.getUserName(),
                new Date(),
                null,
                null,
                null,
                null,
                AuditLogEntry.TYPE_FIELD_CHANGE,
                name,
                oldValue,
                newValue);
        auditLogService.log(ent);
    }
}
