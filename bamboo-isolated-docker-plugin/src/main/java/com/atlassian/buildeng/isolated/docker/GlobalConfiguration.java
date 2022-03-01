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
import com.atlassian.buildeng.isolated.docker.yaml.YamlStorage;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.sax.Link;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;


/**
 * Spring component that provides access to settings set in the administration panel.
 */
@BambooComponent
public class GlobalConfiguration {

    static String BANDANA_DEFAULT_IMAGE = "com.atlassian.buildeng.pbc.default.image";
    static String BANDANA_MAX_AGENT_CREATION_PER_MINUTE = "com.atlassian.buildeng.pbc.default.max.agent.creation.rate";
    static String BANDANA_ARCHITECTURE_CONFIG = "com.atlassian.buildeng.pbc.architecture.config";

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

    public String getArchitectureConfigAsString() {
        YamlStorage<String> architectureConfig = getArchitectureConfigStorage(bandanaManager);
        return architectureConfig != null ? architectureConfig.getRawString() : "";
    }

    /**
     * @return An unmodifiable view of the architecture config, since the reference that Bandana will provide is the actual
     * map where the data is stored and can be modified! Any changes to the original list will be reflected globally.
     * Use {@code new LinkedHashMap<>(getArchitectureConfig())} if you need a mutable map.
     */
    public Map<String, String> getArchitectureConfig() {
        return getArchitectureConfigWithBandana(this.bandanaManager);
    }

    // These two methods are separated so that this can easily be called
    public static Map<String, String> getArchitectureConfigWithBandana(BandanaManager bandanaManager) {
        YamlStorage<String> architectureConfig = getArchitectureConfigStorage(bandanaManager);
        return architectureConfig != null ?
                Collections.unmodifiableMap(architectureConfig.getParsedYaml()) : new LinkedHashMap<>();
    }

    /**
     * Saves changes to the configuration.
     */
    public void persist(Config config) {
        String defaultImage = config.getDefaultImage();
        Integer maxAgentCreationPerMinute = config.getMaxAgentCreationPerMinute();
        String archRawString = config.getArchitectureConfig();

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

        if (!(archRawString.equals(getArchitectureConfigAsString()))) {
            auditLogEntry("PBC Architectures supported",
                    getArchitectureConfigAsString(), archRawString);

            Map<String, String> yaml = null;
            if (StringUtils.isNotBlank(archRawString)) {
                Yaml yamlParser = new Yaml(new SafeConstructor());
                try {
                    // Will be loaded as a LinkedHashMap, and we want to keep that to preserver ordering (i.e. default on top)
                    yaml = (LinkedHashMap<String, String>) yamlParser.load(archRawString);
                }
                catch (YAMLException e) {
                    throw new IllegalArgumentException("Received invalid YAML for architecture list");
                }
            }

            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                    BANDANA_ARCHITECTURE_CONFIG, new YamlStorage<>(archRawString, yaml));
        }
    }


    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new AuditLogMessage(authenticationContext.getUserName(),
                new Date(), null, null, AuditLogEntry.TYPE_FIELD_CHANGE, name, oldValue, newValue);
        auditLogService.log(ent);
    }

    private static YamlStorage<String> getArchitectureConfigStorage(BandanaManager bandanaManager) {
        return (YamlStorage<String>) bandanaManager
                .getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG);
    }
}

