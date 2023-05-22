/*
 * Copyright 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.handler;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.build.docker.DockerHandler;
import com.atlassian.bamboo.build.docker.DockerHandlerProvider;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.plugin.descriptor.DockerHandlerModuleDescriptor;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.GlobalConfiguration;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.sal.api.features.DarkFeatureManager;
import com.opensymphony.xwork2.TextProvider;
import java.util.Map;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerHandlerProviderImpl implements DockerHandlerProvider {

    private final Logger logger = LoggerFactory.getLogger(DockerHandlerProviderImpl.class);

    static final String ISOLATION_TYPE = "PBC";
    public static final String PBC_KEY = "custom.isolated.docker.enabled";

    private DockerHandlerModuleDescriptor moduleDescriptor;
    private final TemplateRenderer templateRenderer;
    private final EnvironmentCustomConfigService environmentCustomConfigService;
    private final EnvironmentRequirementService environmentRequirementService;
    private final WebResourceManager webResourceManager;
    private final GlobalConfiguration globalConfiguration;
    private final Validator validator;
    private final DarkFeatureManager darkFeatureManager;

    /**
     * New stateless instance.
     */
    @Inject
    public DockerHandlerProviderImpl(
            TemplateRenderer templateRenderer,
            EnvironmentCustomConfigService environmentCustomConfigService,
            EnvironmentRequirementService environmentRequirementService,
            WebResourceManager webResourceManager,
            GlobalConfiguration globalConfiguration,
            Validator validator,
            DarkFeatureManager darkFeatureManager) {
        this.templateRenderer = templateRenderer;
        this.environmentCustomConfigService = environmentCustomConfigService;
        this.environmentRequirementService = environmentRequirementService;
        this.webResourceManager = webResourceManager;
        this.globalConfiguration = globalConfiguration;
        this.validator = validator;
        this.darkFeatureManager = darkFeatureManager;
    }

    @Override
    public String getIsolationType() {
        return ISOLATION_TYPE;
    }

    @Override
    public DockerHandler getHandler(BuildDefinition bd, boolean create) {
        Configuration c = ConfigurationBuilder.create(globalConfiguration.getDefaultImage())
                .withEnabled(false)
                .build();
        if (bd != null) {
            c = AccessConfiguration.forBuildDefinition(bd);
            if (c.getDockerImage().equals("")) {
                c.setDockerImage(globalConfiguration.getDefaultImage());
            }
        }
        return new DockerHandlerImpl(
                moduleDescriptor,
                webResourceManager,
                templateRenderer,
                environmentCustomConfigService,
                environmentRequirementService,
                create,
                c,
                globalConfiguration,
                validator,
                globalConfiguration.getEnabledProperty());
    }

    @Override
    public DockerHandler getHandler(Environment environment, boolean create) {
        Configuration c = ConfigurationBuilder.create(globalConfiguration.getDefaultImage())
                .withEnabled(false)
                .build();
        if (environment != null) {
            c = AccessConfiguration.forEnvironment(environment, environmentCustomConfigService);
            if (c.getDockerImage().equals("")) {
                c.setDockerImage(globalConfiguration.getDefaultImage());
            }
        }
        return new DockerHandlerImpl(
                moduleDescriptor,
                webResourceManager,
                templateRenderer,
                environmentCustomConfigService,
                environmentRequirementService,
                create,
                c,
                globalConfiguration,
                validator,
                globalConfiguration.getEnabledProperty());
    }

    @Override
    public DockerHandler getHandler(Map<String, Object> webFragmentsContextMap, boolean create) {
        Configuration c = DockerHandlerImpl.createFromWebContext(webFragmentsContextMap);
        return new DockerHandlerImpl(
                moduleDescriptor,
                webResourceManager,
                templateRenderer,
                environmentCustomConfigService,
                environmentRequirementService,
                create,
                c,
                globalConfiguration,
                validator,
                globalConfiguration.getEnabledProperty());
    }

    @Override
    public void init(@NotNull DockerHandlerModuleDescriptor dockerHandlerModuleDescriptor) {
        this.moduleDescriptor = dockerHandlerModuleDescriptor;
    }

    @Override
    public String getIsolationTypeLabel(TextProvider textProvider) {
        return "Per Build Container (PBC) plugin";
    }

    @Override
    /*
     * buildDefinition is used for builds
     */
    public boolean isCustomDedicatedAgentExpected(BuildDefinition buildDefinition) {
        boolean result = isEphemeral() && isPbcBuild(buildDefinition);
        logger.debug("Agent is showing {} for result of isCustomDedicatedAgentExpected build", result);
        return result;
    }

    @Override
    /*
     * environmentCustomConfig is used for deployments
     */
    public boolean isCustomDedicatedAgentExpected(Map<String, String> environmentCustomConfig) {
        boolean result = isEphemeral() && isPbcDeployment(environmentCustomConfig);
        logger.debug("Agent is showing {} for result of isCustomDedicatedAgentExpected deployment", result);
        return result;
    }

    @Override
    public String getEnvironmentConfigurationKey() {
        return CustomEnvironmentConfigExporterImpl.ENV_CONFIG_MODULE_KEY;
    }

    private boolean isPbcBuild(BuildDefinition buildDefinition) {
        if (buildDefinition == null) {
            return false;
        }
        return Boolean.parseBoolean(buildDefinition.getCustomConfiguration().get(PBC_KEY));
    }

    private boolean isPbcDeployment(Map<String, String> environmentCustomConfig) {
        if (environmentCustomConfig == null) {
            return false;
        }
        return Boolean.parseBoolean(environmentCustomConfig.get(PBC_KEY));
    }

    private boolean isEphemeral() {
        return darkFeatureManager
                .isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED)
                .orElse(false);
    }
}
