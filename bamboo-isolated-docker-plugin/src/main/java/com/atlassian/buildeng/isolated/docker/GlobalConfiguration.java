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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.isolated.docker.rest.Config;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;


/**
 * Spring component that provides access to settings set in the administration panel.
 */
public class GlobalConfiguration {

    static String BANDANA_DEFAULT_IMAGE = "com.atlassian.buildeng.pbc.default.image";
    static String BANDANA_MAX_AGENT_CREATION_PER_MINUTE = "com.atlassian.buildeng.pbc.default.agents";

    private final BandanaManager bandanaManager;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    public GlobalConfiguration(BandanaManager bandanaManager, AuditLogService auditLogService,
                               BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }

    public String getDefaultImage() {
        String image = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DEFAULT_IMAGE);
        return image != null ? image : "";
    }

    public Integer getMaxAgentCreationPerMinute() {
        Integer maxAgents = (Integer) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_MAX_AGENT_CREATION_PER_MINUTE);
        return maxAgents != null ? maxAgents : 100;
    }

    /**
     * Saves changes to the configuration.
     */
    public void persist(Config config) {
        String defaultImage = config.getDefaultImage();
        Integer maxAgentCreationPerMinute = config.getMaxAgentCreationPerMinute();

        if (!StringUtils.equals(defaultImage, getDefaultImage())) {
            auditLogEntry("PBC Default Image", getDefaultImage(), defaultImage);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DEFAULT_IMAGE, defaultImage);
        }
        if (!(maxAgentCreationPerMinute.equals(getMaxAgentCreationPerMinute()))) {
            auditLogEntry("PBC Maximum Number of Agent Creation Per Minute",
                    Integer.toString(getMaxAgentCreationPerMinute()), Integer.toString(maxAgentCreationPerMinute));
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                    BANDANA_MAX_AGENT_CREATION_PER_MINUTE, maxAgentCreationPerMinute);
        }
    }


    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new AuditLogMessage(authenticationContext.getUserName(),
                new Date(), null, null, AuditLogEntry.TYPE_FIELD_CHANGE, name, oldValue, newValue);
        auditLogService.log(ent);
    }
}

