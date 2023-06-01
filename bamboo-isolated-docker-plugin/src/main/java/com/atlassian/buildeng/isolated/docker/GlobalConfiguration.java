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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.isolated.docker.rest.Config;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Spring component that provides access to settings set in the administration panel.
 * WARNING: Do not try and be smart and use custom container classes to store config. A different class loader will be
 * used each time a plugin is reloaded. If you store a custom class in Bandana, you will get a ClassCastException when
 * attempting to cast it back to the intended class. Stick to Java built-ins.
 */
@Component
@ExportAsService
public class GlobalConfiguration implements LifecycleAware {
    static final String BANDANA_ENABLED_PROPERTY = "com.atlassian.buildeng.pbc.enabled";
    static final String BANDANA_DEFAULT_IMAGE = "com.atlassian.buildeng.pbc.default.image";
    static final String BANDANA_MAX_AGENT_CREATION_PER_MINUTE =
            "com.atlassian.buildeng.pbc.default.max.agent.creation.rate";
    static final String BANDANA_AGENT_CLEANUP_TIME = "com.atlassian.buildeng.pbc.agent.cleanup.time";
    static final String BANDANA_AGENT_REMOVAL_TIME = "com.atlassian.buildeng.pbc.agent.removal_time";

    // See class Javadoc about why these are stored separately
    static final String BANDANA_ARCHITECTURE_CONFIG_RAW = "com.atlassian.buildeng.pbc.architecture.config.raw";
    static final String BANDANA_ARCHITECTURE_CONFIG_PARSED = "com.atlassian.buildeng.pbc.architecture.config.parsed";

    public static final String BANDANA_VENDOR_CONFIG = "com.atlassian.buildeng.pbc.vendor";
    public static String VENDOR_AWS = "aws";

    private final BandanaManager bandanaManager;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;

    private final Logger logger = LoggerFactory.getLogger(GlobalConfiguration.class);

    @Inject
    public GlobalConfiguration(
            BandanaManager bandanaManager,
            AuditLogService auditLogService,
            BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }

    @NotNull
    public String getDefaultImage() {
        String image = getDefaultImageRaw();
        return image != null ? image : "";
    }

    /**
     * Retrieves the raw default image from Bandana, without checking for null. This method should
     * not be used unless you need to check whether this value is null explicitly.
     *
     * @return the raw default image value from Bandana, which may be null
     */
    @Nullable
    private String getDefaultImageRaw() {
        return (String) bandanaManager.getValue(
                PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_DEFAULT_IMAGE);
    }

    @NotNull
    public Integer getMaxAgentCreationPerMinute() {
        Integer maxAgents = getMaxAgentCreationPerMinuteRaw();
        return maxAgents != null ? maxAgents : 100;
    }

    /**
     * Retrieves the raw max agent creation rate limit from Bandana, without checking for null. This method should
     * not be used unless you need to check whether this value is null explicitly.
     *
     * @return the raw max agent creation rate limit value from Bandana, which may be null
     */
    @Nullable
    private Integer getMaxAgentCreationPerMinuteRaw() {
        return (Integer)
                bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_MAX_AGENT_CREATION_PER_MINUTE);
    }

    @NotNull
    public Integer getAgentCleanupTime() {
        Integer cleanupTimeRaw = getAgentCleanupTimeRaw();
        return cleanupTimeRaw != null ? cleanupTimeRaw : Constants.DEFAULT_AGENT_CLEANUP_DELAY;
    }

    @Nullable
    private Integer getAgentCleanupTimeRaw() {
        return (Integer) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AGENT_CLEANUP_TIME);
    }

    @NotNull
    public Integer getAgentRemovalTime() {
        Integer removalTimeRaw = getAgentRemovalTimeRaw();
        return removalTimeRaw != null ? removalTimeRaw : Constants.DEFAULT_AGENT_REMOVE_DELAY;
    }

    @Nullable
    private Integer getAgentRemovalTimeRaw() {
        return (Integer) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AGENT_REMOVAL_TIME);
    }

    @NotNull
    public Boolean getEnabledProperty() {
        Boolean enabled = getEnabledRaw();
        return enabled != null ? enabled : false;
    }

    /**
     * Retrieves the raw enabled property from Bandana, without checking for null. This method should not be used
     * unless you need to check whether this value is null explicitly.
     *
     * @return the raw enabled value from Bandana, which may be null
     */
    @Nullable
    private Boolean getEnabledRaw() {
        return (Boolean) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ENABLED_PROPERTY);
    }

    @NotNull
    public String getArchitectureConfigAsString() {
        String architectureConfig = getArchitectureConfigAsStringRaw();
        return architectureConfig != null ? architectureConfig : "";
    }

    /**
     * Retrieves the raw architecture config from Bandana, without checking for null. This method should not be used
     * unless you need to check whether this value is null explicitly.
     *
     * @return the raw architecture config from Bandana, which may be null
     */
    private String getArchitectureConfigAsStringRaw() {
        String architectureConfig = (String)
                bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG_RAW);
        return architectureConfig;
    }

    @NotNull
    public String getVendor() {
        return getVendorWithBandana(bandanaManager);
    }

    /**
     * Get the architecture config.
     *
     * @return An unmodifiable view of the architecture config, since the reference that Bandana will provide is the
     *         actual
     *         map where the data is stored and can be modified! Any changes to the original map will be reflected
     *         globally.
     *         Use {@code new LinkedHashMap<>(getArchitectureConfig())} if you need a mutable map.
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
     * @return An unmodifiable view of the architecture config, since the reference that Bandana will provide is the
     *         actual
     *         map where the data is stored and can be modified! Any changes to the original map will be reflected
     *         globally.
     *         Use {@code new LinkedHashMap<>(getArchitectureConfig())} if you need a mutable map.
     */
    public static Map<String, String> getArchitectureConfigWithBandana(BandanaManager bandanaManager) {
        LinkedHashMap<String, String> architectureConfig = (LinkedHashMap<String, String>)
                bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG_PARSED);
        return architectureConfig != null ? Collections.unmodifiableMap(architectureConfig) : new LinkedHashMap<>();
    }

    public static String getVendorWithBandana(BandanaManager bandanaManager) {
        String vendor = getVendorWithBandanaRaw(bandanaManager);
        return vendor == null ? "" : vendor;
    }

    /**
     * Retrieves the raw vendor from Bandana, without checking for null. This method should not be used
     * unless you need to check whether this value is null explicitly.
     *
     * @return the raw vendor value from Bandana, which may be null
     */
    public static String getVendorWithBandanaRaw(BandanaManager bandanaManager) {
        String vendor = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_VENDOR_CONFIG);
        return vendor;
    }

    /**
     * Saves changes to the configuration.
     */
    public void persist(Config config) {
        final String defaultImage = config.getDefaultImage();
        final Integer maxAgentCreationPerMinute = config.getMaxAgentCreationPerMinute();
        final String archRawString = config.getArchitectureConfig();
        final boolean awsVendor = config.isAwsVendor();
        final Boolean enabled = config.isEnabled();
        final Integer agentCleanupTime = config.getAgentCleanupTime();
        final Integer agentRemovalTime = config.getAgentRemovalTime();

        logger.info("Applying new PBC config: " + config);

        // Don't use non-static Instance.equals() methods, if the new object is null, you will get NPE

        if (!StringUtils.equals(defaultImage, getDefaultImage())) {
            auditLogEntry("PBC Default Image", getDefaultImage(), defaultImage);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DEFAULT_IMAGE, defaultImage);
        }

        if (!Objects.equals(maxAgentCreationPerMinute, getMaxAgentCreationPerMinute())) {
            auditLogEntry(
                    "PBC Maximum Number of Agent Creation Per Minute",
                    Integer.toString(getMaxAgentCreationPerMinute()),
                    Integer.toString(maxAgentCreationPerMinute));
            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT,
                    BANDANA_MAX_AGENT_CREATION_PER_MINUTE,
                    maxAgentCreationPerMinute);
        }
        if (!Objects.equals(agentCleanupTime, getAgentCleanupTime())) {
            auditLogEntry(
                    "PBC agent cleanup time",
                    Integer.toString(getAgentCleanupTime()),
                    Integer.toString(agentCleanupTime));
            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AGENT_CLEANUP_TIME, agentCleanupTime);
        }
        if (!Objects.equals(agentRemovalTime, getAgentRemovalTime())) {
            auditLogEntry(
                    "PBC agent removal time",
                    Integer.toString(getAgentRemovalTime()),
                    Integer.toString(agentRemovalTime));
            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_AGENT_REMOVAL_TIME, agentRemovalTime);
        }
        if (enabled != null && !enabled.equals(getEnabledProperty())) {
            auditLogEntry("PBC Global Enable Flag", Boolean.toString(getEnabledProperty()), Boolean.toString(enabled));
            setEnabledProperty(enabled);
        }

        if (awsVendor) {
            if (!VENDOR_AWS.equals(getVendor())) {
                auditLogEntry("PBC Vendor", getVendor(), VENDOR_AWS);
                setVendorWithBandana(bandanaManager, VENDOR_AWS);
            }
        } else {
            if (isNotBlank(getVendor())) {
                auditLogEntry("PBC Vendor", getVendor(), "");
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_VENDOR_CONFIG, "");
            }
        }

        if (!StringUtils.equals(archRawString, getArchitectureConfigAsString())) {
            auditLogEntry("PBC Architectures supported", getArchitectureConfigAsString(), archRawString);

            LinkedHashMap<String, String> yaml = null;
            if (isNotBlank(archRawString)) {
                Yaml yamlParser = new Yaml(new SafeConstructor(new LoaderOptions()));
                try {
                    // Will be loaded as a LinkedHashMap, and we want to keep that to preserver ordering (i.e. default
                    // on top)
                    Object uncastYaml = yamlParser.load(archRawString);
                    if (uncastYaml instanceof LinkedHashMap) {
                        yaml = (LinkedHashMap) uncastYaml;
                        for (Map.Entry entry : yaml.entrySet()) {
                            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                                throw new IllegalArgumentException(
                                        "Architecture configuration must be a map from String to" + " String, but "
                                                + entry
                                                + " was not!");
                            }
                        }
                        yaml = (LinkedHashMap<String, String>) uncastYaml;
                    } else {
                        throw new IllegalArgumentException("Received invalid YAML for architecture list");
                    }

                } catch (YAMLException e) {
                    throw new IllegalArgumentException("Received invalid YAML for architecture list");
                }
            }

            bandanaManager.setValue(
                    PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG_RAW, archRawString);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ARCHITECTURE_CONFIG_PARSED, yaml);
        }
    }

    public static void setVendorWithBandana(BandanaManager bandanaManager, String vendor) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_VENDOR_CONFIG, vendor);
    }

    @VisibleForTesting
    void setEnabledProperty(Boolean enabled) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ENABLED_PROPERTY, enabled);
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

    void migrateEnabled() {
        Boolean enabled = getEnabledRaw();
        if (enabled == null) {
            boolean optionsNotNull = Stream.of(
                            getDefaultImageRaw(), getMaxAgentCreationPerMinuteRaw(), getArchitectureConfigAsStringRaw())
                    .anyMatch(Objects::nonNull);

            if (optionsNotNull) {
                logger.info("Detected non-null PBC settings but PBC enable status was null! Now enabling PBC.");
                auditLogEntry("PBC Global Enable Flag", "null", "true");
                setEnabledProperty(true);
            }
        }
    }

    @Override
    public void onStart() {
        migrateEnabled();
    }

    @Override
    public void onStop() {
        // We don't need to do anything on stop for this class
    }
}
