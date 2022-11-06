/*
 * Copyright 2016 - 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.metrics;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import com.google.common.base.Preconditions;
import java.util.Date;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;

@BambooComponent
public class GlobalConfiguration {

    static String BANDANA_PROMETHEUS_URL = "com.atlassian.buildeng.pbc.kubernetes.metrics.prometheus";

    private final BandanaManager bandanaManager;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    @Inject
    public GlobalConfiguration(BandanaManager bandanaManager,
                               AuditLogService auditLogService,
                               BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }

    public String getPrometheusUrl() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_PROMETHEUS_URL);
    }


    /**
     * Saves changes to the configuration.
     */
    public void persist(String prometheusUrl) {
        Preconditions.checkArgument(StringUtils.isNotBlank(prometheusUrl), "Prometheus URL is mandatory");
        if (!StringUtils.equals(prometheusUrl, getPrometheusUrl())) {
            auditLogEntry("PBC Kubernetes Prometheus URL", getPrometheusUrl(), prometheusUrl);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_PROMETHEUS_URL, prometheusUrl);
        }
    }


    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new AuditLogMessage(authenticationContext.getUserName(),
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
