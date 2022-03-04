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
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;


/**
 * Spring component that provides access to settings set in the administration panel.
 *
 * WARNING: Do not try and be smart and use custom container classes to store config. A different class loader will be
 * used each time a plugin is reloaded. If you store a custom class in Bandana, you will get a ClassCastException when
 * attempting to cast it back to the intended class. Stick to Java built-ins.
 */
@BambooComponent
public class GlobalConfiguration {
    private final Logger logger = LoggerFactory.getLogger(GlobalConfiguration.class);

    static String BANDANA_DEFAULT_IMAGE = "com.atlassian.buildeng.pbc.default.image";
    static String BANDANA_MAX_AGENT_CREATION_PER_MINUTE = "com.atlassian.buildeng.pbc.default.max.agent.creation.rate";
    // See Javadoc
    static String BANDANA_ARCHITECTURE_CONFIG_RAW = "com.atlassian.buildeng.pbc.architecture.config.raw";
    static String BANDANA_ARCHITECTURE_CONFIG_PARSED = "com.atlassian.buildeng.pbc.architecture.config.parsed";


    private final BandanaManager bandanaManager;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    public GlobalConfiguration(BandanaManager bandanaManager, AuditLogService auditLogService,
                               BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }

    @NotNull
    public String getDefaultImage() {
        String image = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DEFAULT_IMAGE);
        return image != null ? image : "";
    }

    @NotNull
    public Integer getMaxAgentCreationPerMinute() {
        Integer maxAgents = (Integer) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_MAX_AGENT_CREATION_PER_MINUTE);
        return maxAgents != null ? maxAgents : 100;
    }

    @NotNull
    public String getArchitectureConfigAsString() {
        String architectureConfig = (String) bandanaManager
                .getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG_RAW);
        return architectureConfig != null ? architectureConfig : "";
    }

    /**
     * @return An unmodifiable view of the architecture config, since the reference that Bandana will provide is the actual
     * map where the data is stored and can be modified! Any changes to the original map will be reflected globally.
     * Use {@code new LinkedHashMap<>(getArchitectureConfig())} if you need a mutable map.
     */
    @NotNull
    public Map<String, String> getArchitectureConfig() {
        return getArchitectureConfigWithBandana(this.bandanaManager);
    }

    /**
     * Static version of getArchitectureConfig(), which requires an instance of {@link BandanaManager} to be passed in.
     * This static version exists to aid in fetching the architecture config from other plugins without needing an
     * instance of this GlobalConfiguration class.
     *
     * @param bandanaManager An instance of {@link BandanaManager} that should be wired from Bamboo
     * @return An unmodifiable view of the architecture config, since the reference that Bandana will provide is the actual
     * map where the data is stored and can be modified! Any changes to the original map will be reflected globally.
     * Use {@code new LinkedHashMap<>(getArchitectureConfig())} if you need a mutable map.
     */
    public static Map<String, String> getArchitectureConfigWithBandana(BandanaManager bandanaManager) {
        LinkedHashMap<String, String> architectureConfig = (LinkedHashMap<String, String>) bandanaManager
                .getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG_PARSED);
        return architectureConfig != null ?
                Collections.unmodifiableMap(architectureConfig) : new LinkedHashMap<>();
    }

    /**
     * Saves changes to the configuration.
     */
    public void persist(Config config) {
        String defaultImage = config.getDefaultImage();
        Integer maxAgentCreationPerMinute = config.getMaxAgentCreationPerMinute();
        String archRawString = config.getArchitectureConfig();

        // Don't use non-static Instance.equals() methods, if the new object is null, you will get NPE

        if (!StringUtils.equals(defaultImage, getDefaultImage())) {
            auditLogEntry("PBC Default Image", getDefaultImage(), defaultImage);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DEFAULT_IMAGE, defaultImage);
        }

        if (!Objects.equals(maxAgentCreationPerMinute, getMaxAgentCreationPerMinute())) {
            auditLogEntry("PBC Maximum Number of Agent Creation Per Minute",
                    Integer.toString(getMaxAgentCreationPerMinute()), Integer.toString(maxAgentCreationPerMinute));
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                    BANDANA_MAX_AGENT_CREATION_PER_MINUTE, maxAgentCreationPerMinute);
        }

        if (!StringUtils.equals(archRawString, getArchitectureConfigAsString())) {
            auditLogEntry("PBC Architectures supported",
                    getArchitectureConfigAsString(), archRawString);

            Map<String, String> yaml = null;
            if (StringUtils.isNotBlank(archRawString)) {
                Yaml yamlParser = new Yaml(new SafeConstructor());
                try {
                    // Will be loaded as a LinkedHashMap, and we want to keep that to preserver ordering (i.e. default on top)
                    Object uncastYaml = yamlParser.load(archRawString);
                    if (uncastYaml instanceof LinkedHashMap) {
                        yaml = (LinkedHashMap) uncastYaml;
                        for (Map.Entry entry: yaml.entrySet()) {
                            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                                throw new IllegalArgumentException("Architecture configuration must be a map from String to" +
                                        " String, but " + entry + " was not!");
                            }
                        }
                        yaml = (LinkedHashMap<String, String>) uncastYaml;
                    }
                    else {
                        throw new IllegalArgumentException("Received invalid YAML for architecture list");
                    }

                }
                catch (YAMLException e) {
                    throw new IllegalArgumentException("Received invalid YAML for architecture list");
                }
            }

            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                    BANDANA_ARCHITECTURE_CONFIG_RAW, archRawString);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                    BANDANA_ARCHITECTURE_CONFIG_PARSED, yaml);
        }
    }


    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new AuditLogMessage(authenticationContext.getUserName(),
                new Date(), null, null, AuditLogEntry.TYPE_FIELD_CHANGE, name, oldValue, newValue);
        auditLogService.log(ent);
    }
}

