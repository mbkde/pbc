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

package com.atlassian.buildeng.simple.backend;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.simple.backend.rest.Config;
import java.util.Date;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

public class GlobalConfiguration {
    private static final String BANDANA_CERTPATH = "com.atlassian.buildeng.simple.backend.certPath";
    private static final String BANDANA_URL = "com.atlassian.buildeng.simple.backend.url";
    private static final String BANDANA_API_VERSION = "com.atlassian.buildeng.simple.backend.apiVersion";
    private static final String BANDANA_SIDEKICK = "com.atlassian.buildeng.simple.backend.sidekick";
    private static final String BANDANA_SIDEKICK_IMAGE = "com.atlassian.buildeng.simple.backend.sidekickImage";
 
    private final BandanaManager bandanaManager;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    public GlobalConfiguration(BandanaManager bandanaManager,AuditLogService auditLogService,
            BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }
    
    public void setDockerConfig(Config config) {
        if (!StringUtils.equals(config.url, getDockerConfig().getUrl())) {
            auditLogEntry("PBC Docker Daemon URL", getDockerConfig().getUrl(), config.url);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_URL, config.url);
        }
        if (!StringUtils.equals(config.certPath, getDockerConfig().getCertPath())) {
            auditLogEntry("PBC Docker Cert Path", getDockerConfig().getCertPath(), config.certPath);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CERTPATH, config.certPath);
        }
        if (!StringUtils.equals(config.apiVersion, getDockerConfig().getApiVersion())) {
            auditLogEntry("PBC Docker API Version", getDockerConfig().getApiVersion(), config.apiVersion);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_API_VERSION, config.apiVersion);
        }
        if (!StringUtils.equals(config.getSidekick(), getDockerConfig().sidekick)) {
            auditLogEntry("PBC Sidekick", getDockerConfig().getSidekick(), config.sidekick);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK, config.sidekick);
        }
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_IMAGE, config.sidekickImage);
    }
    
    public Config getDockerConfig() {
        String api = StringUtils.defaultString((String)bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, 
                BANDANA_API_VERSION), "");
        String url = StringUtils.defaultString((String)bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, 
                BANDANA_URL), "");
        String certPath = StringUtils.defaultString((String)bandanaManager.getValue(
                PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CERTPATH), "");
        String sidekick = StringUtils.defaultString((String)bandanaManager.getValue(
                PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK), "");
        boolean image = BooleanUtils.toBooleanDefaultIfNull((Boolean) bandanaManager.getValue(
                PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_IMAGE), true);
        return new Config(api, certPath, url, sidekick, image);
    }
    
    public ProcessBuilder decorateCommands(ProcessBuilder pb) {
        Config config = getDockerConfig();
        if (StringUtils.isNotBlank(config.getUrl())) {
            pb.environment().put("DOCKER_HOST", config.getUrl());
        }
        if (StringUtils.isNotBlank(config.getApiVersion())) {
            pb.environment().put("DOCKER_API_VERSION", config.getApiVersion());
        }
        if (StringUtils.isNotBlank(config.getCertPath())) {
            pb.environment().put("DOCKER_CERT_PATH", config.getCertPath());
            pb.environment().put("DOCKER_TLS_VERIFY", "1");
        }
        return pb;
    }
    
    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new  AuditLogMessage(authenticationContext.getUserName(), new Date(),
                null, null, null, null,
                AuditLogEntry.TYPE_FIELD_CHANGE, name, oldValue, newValue);
        auditLogService.log(ent);
    }
    
}
