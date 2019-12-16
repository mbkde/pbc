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
import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.build.docker.DockerHandler;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.exception.WebValidationException;
import com.atlassian.bamboo.struts.OgnlStackUtils;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.ConfigUtils;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.v2.build.requirement.ImmutableRequirement;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.isolated.docker.lifecycle.BuildProcessorServerImpl;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.elements.ResourceLocation;
import com.atlassian.plugin.webresource.WebResourceManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerHandlerImpl implements DockerHandler {

    private static final Logger log = LoggerFactory.getLogger(DockerHandlerImpl.class);
    private final ModuleDescriptor moduleDescriptor;
    private final TemplateRenderer templateRenderer;
    private final EnvironmentCustomConfigService environmentCustomConfigService;
    private final boolean create;
    private final Configuration configuration;
    private final WebResourceManager webResourceManager;
    private final EnvironmentRequirementService environmentRequirementService;

    /**
     * Creates new stateful instance.
     */

    public DockerHandlerImpl(ModuleDescriptor moduleDescriptor, WebResourceManager webResourceManager, 
            TemplateRenderer templateRenderer, 
            EnvironmentCustomConfigService environmentCustomConfigService,
            EnvironmentRequirementService environmentRequirementService,
            boolean create, Configuration configuration) {
        this.moduleDescriptor = moduleDescriptor;
        this.templateRenderer = templateRenderer;
        this.environmentCustomConfigService = environmentCustomConfigService;
        this.environmentRequirementService = environmentRequirementService;
        this.create = create;
        this.configuration = configuration;
        this.webResourceManager = webResourceManager;
    }

    

    @Override
    public String getEditHtml() {
        return render("edit");
    }

    @Override
    public String getViewHtml() {
        return render("view");
    }

    @Override
    public boolean isEnabled() {
        return configuration.isEnabled();
    }

    @Override
    public String getIsolationType() {
        return DockerHandlerProviderImpl.ISOLATION_TYPE;
    }

    @Override
    public ErrorCollection validateConfig(Map<String, Object> webFragmentsContextMap) {
        String extraCont = (String) webFragmentsContextMap.get(Configuration.DOCKER_EXTRA_CONTAINERS);
        String size = (String) webFragmentsContextMap.get(Configuration.DOCKER_IMAGE_SIZE);
        String image = (String) webFragmentsContextMap.get(Configuration.DOCKER_IMAGE);
        String role = (String) webFragmentsContextMap.get(Configuration.DOCKER_AWS_ROLE);
        if (StringUtils.isBlank(role)) {
            role = null;
        }
        String enabled = (String) webFragmentsContextMap.get(Configuration.ENABLED_FOR_JOB);
        SimpleErrorCollection errs = new SimpleErrorCollection();
        Validator.validate(image, size, role, extraCont, errs, false);
        return errs;
    }

    @Override
    public void enableAndUpdate(BuildDefinition buildDefinition, Job job, Map<String, Object> webFragmentsContextMap) {
        Configuration config = createFromWebContext(webFragmentsContextMap);
        Map<String, String> cc = buildDefinition.getCustomConfiguration();

        cc.put(Configuration.ENABLED_FOR_JOB, "true");
        cc.put(Configuration.DOCKER_IMAGE, config.getDockerImage());
        cc.put(Configuration.DOCKER_IMAGE_SIZE, config.getSize().name());
        cc.put(Configuration.DOCKER_AWS_ROLE, config.getAwsRole());
        cc.put(Configuration.DOCKER_EXTRA_CONTAINERS,
                (String)webFragmentsContextMap.getOrDefault(Configuration.DOCKER_EXTRA_CONTAINERS, "[]"));

        removeAllRequirements(job.getRequirementSet());
        addResultRequirement(job.getRequirementSet());
    }

    @Override
    public void enableAndUpdate(Environment environment, Map<String, Object> webFragmentsContextMap) {
        Configuration config = createFromWebContext(webFragmentsContextMap);
        Map<String, Map<String, String>> all = environmentCustomConfigService.getEnvironmentPluginConfig(
                environment.getId());
        Map<String, String> cc = all.get(CustomEnvironmentConfigExporterImpl.ENV_CONFIG_MODULE_KEY);
        if (cc == null) {
            cc = new HashMap<>();
            all.put(CustomEnvironmentConfigExporterImpl.ENV_CONFIG_MODULE_KEY, cc);
        }

        cc.put(Configuration.ENABLED_FOR_JOB, "true");
        cc.put(Configuration.DOCKER_IMAGE, config.getDockerImage());
        cc.put(Configuration.DOCKER_IMAGE_SIZE, config.getSize().name());
        cc.put(Configuration.DOCKER_AWS_ROLE, config.getAwsRole());
        cc.put(Configuration.DOCKER_EXTRA_CONTAINERS,
                (String)webFragmentsContextMap.getOrDefault(Configuration.DOCKER_EXTRA_CONTAINERS, "[]"));
        environmentCustomConfigService.saveEnvironmentPluginConfig(all, environment.getId());
        removeEnvironmentRequirements(environment, environmentRequirementService);
        addEnvironementRequirement(environment, environmentRequirementService);
    }

    @Override
    public void disable(BuildDefinition buildDefinition, Job job) {
        Map<String, String> cc = buildDefinition.getCustomConfiguration();
        cc.put(Configuration.ENABLED_FOR_JOB, "false");
        removeAllRequirements(job.getRequirementSet());
        //TODO do we remove the other configuration at this point?
    }

    @Override
    public void disable(Environment environment) {
        Map<String, Map<String, String>> all = environmentCustomConfigService.getEnvironmentPluginConfig(
                environment.getId());
        Map<String, String> cc = all.get(CustomEnvironmentConfigExporterImpl.ENV_CONFIG_MODULE_KEY);
        if (cc == null) {
            cc = new HashMap<>();
            all.put(CustomEnvironmentConfigExporterImpl.ENV_CONFIG_MODULE_KEY, cc);
        }
        cc.put(Configuration.ENABLED_FOR_JOB, "false");
        environmentCustomConfigService.saveEnvironmentPluginConfig(all, environment.getId());
        removeEnvironmentRequirements(environment, environmentRequirementService);
    }

    static void removeEnvironmentRequirements(Environment environment, 
            EnvironmentRequirementService environmentRequirementService) {
        try {
            environmentRequirementService.getRequirementsForEnvironment(environment.getId()).stream()
                    .filter((ImmutableRequirement input)
                        -> input.getKey().equals(BuildProcessorServerImpl.CAPABILITY)
                               || input.getKey().equals(Constants.CAPABILITY_RESULT))
                    .forEach((ImmutableRequirement t) -> {
                        try {
                            environmentRequirementService.removeRequirement(environment.getId(), t.getId());
                        } catch (WebValidationException ex) {
                            log.error("Failed to remove requirement for environment " + environment.getId(), ex);
                        }
                    });
        } catch (WebValidationException ex) {
            log.error("Failed to list requirements for environment " + environment.getId(), ex);
        }
    }

    @Override
    public void appendConfiguration(BuildConfiguration buildConfiguration, Map<String, Object> webFragmentsContextMap,
            boolean enabled) {
        Configuration config = createFromWebContext(webFragmentsContextMap);
        final HierarchicalConfiguration hc = new HierarchicalConfiguration();
        hc.setDelimiterParsingDisabled(true);
        hc.setProperty(Configuration.ENABLED_FOR_JOB, enabled);
        hc.setProperty(Configuration.DOCKER_IMAGE, config.getDockerImage());
        hc.setProperty(Configuration.DOCKER_IMAGE_SIZE, config.getSize().name());
        hc.setProperty(Configuration.DOCKER_AWS_ROLE, config.getAwsRole());
        hc.setProperty(Configuration.DOCKER_EXTRA_CONTAINERS, 
                (String)webFragmentsContextMap.getOrDefault(Configuration.DOCKER_EXTRA_CONTAINERS, "[]"));
        buildConfiguration.clearTree(Configuration.PROPERTY_PREFIX);
        ConfigUtils.copyNodes(hc, buildConfiguration.getProjectConfig());
        //we deal with adding the requirement Constants.CAPABILITY_RESULT in BuildCreatedEventListener
        // in here the job doesn't exist yet.
    }
    
    private String render(String name) {
        final ResourceLocation resourceLocation = moduleDescriptor.getResourceLocation("freemarker", name);
        if (resourceLocation != null) {
            final Map<String, Object> context = new HashMap<>();
            context.put(Configuration.DOCKER_IMAGE, configuration.getDockerImage());
            context.put(Configuration.DOCKER_IMAGE_SIZE, configuration.getSize().name());
            context.put(Configuration.DOCKER_AWS_ROLE, configuration.getAwsRole());
            context.put("imageSizes", getImageSizes());
            context.put(Configuration.DOCKER_EXTRA_CONTAINERS, 
                    ConfigurationPersistence.toJson(configuration.getExtraContainers()).toString());
            OgnlStackUtils.putAll(context);
            
            context.put("webResourceManager", webResourceManager);
            Map<String, Object> cc = new HashMap<>();
            cc.put("image", configuration.getDockerImage());
            cc.put("imageSize", configuration.getSize().name());
            cc.put("awsRole", configuration.getAwsRole());
            cc.put("extraContainers", ConfigurationPersistence.toJson(configuration.getExtraContainers()).toString());
            context.put("custom", Collections.singletonMap("isolated", Collections.singletonMap("docker", cc)));

            String templatePath = resourceLocation.getLocation();
            return templateRenderer.render(templatePath, context);
        } else {
            return StringUtils.EMPTY;
        }
    }
    
    static Configuration createFromWebContext(Map<String, Object> webFragmentsContextMap) {
        String v = (String) webFragmentsContextMap.get(Configuration.DOCKER_EXTRA_CONTAINERS);
        String role = (String) webFragmentsContextMap.get(Configuration.DOCKER_AWS_ROLE);
        if (StringUtils.isBlank(role)) {
            role = null;
        }
        Configuration config = ConfigurationBuilder
                .create((String) webFragmentsContextMap.getOrDefault(Configuration.DOCKER_IMAGE, ""))
                .withEnabled(true)
                .withImageSize(Configuration.ContainerSize.valueOf(
                        (String)webFragmentsContextMap.getOrDefault(Configuration.DOCKER_IMAGE_SIZE,
                                Configuration.ContainerSize.REGULAR.name())))
                .withExtraContainers(ConfigurationPersistence.fromJsonString(
                        (String)webFragmentsContextMap.getOrDefault(Configuration.DOCKER_EXTRA_CONTAINERS, "[]")))
                .withAwsRole(role)
                .build();
        return config;
    }

    /**
     * remove pbc requirements from job.
     */
    public static void removeAllRequirements(@NotNull RequirementSet requirementSet) {
        requirementSet.removeRequirements(
            (Requirement input) -> input.getKey().equals(BuildProcessorServerImpl.CAPABILITY)
                                || input.getKey().equals(Constants.CAPABILITY_RESULT));
    }

    /**
     * add pbc requirement to job.
     */
    public static void addResultRequirement(@NotNull RequirementSet requirementSet) {
        requirementSet.addRequirement(new RequirementImpl(Constants.CAPABILITY_RESULT, true, ".*", true));
    }
    
    static void addEnvironementRequirement(Environment environment,
            EnvironmentRequirementService environmentRequirementService) {
        try {
            environmentRequirementService.addRequirement(environment.getId(), 
                    Constants.CAPABILITY_RESULT, ImmutableRequirement.MatchType.MATCHES, ".*");
        } catch (WebValidationException ex) {
            log.error("Failed to add requirement for environment " + environment.getId(), ex);
        }
    }

    /**
     * list of value to label mappings for image sizes.
     */
    @NotNull
    public static Collection<Pair<String, String>> getImageSizes() {
        return Arrays.asList(
            //this is stupid ordering but we want to keep regular as default for new
            //config. but somehow unlike with tasks there's no way to get the defaults propagated into UI.
            Pair.make(Configuration.ContainerSize.REGULAR.name(), "Regular (~8G memory, 2 vCPU)"),
            Pair.make(Configuration.ContainerSize.XSMALL.name(), "Extra Small (~2G memory, 0.5 vCPU)"),
            Pair.make(Configuration.ContainerSize.SMALL.name(), "Small (~4G memory, 1 vCPU)"),
            Pair.make(Configuration.ContainerSize.LARGE.name(), "Large (~12G memory, 3 vCPU)"),
            Pair.make(Configuration.ContainerSize.XLARGE.name(), "Extra Large (~16G memory, 4 vCPU)"),
            Pair.make(Configuration.ContainerSize.XXLARGE.name(), "Extra Extra Large (~20G memory, 5 vCPU)"),
            Pair.make(Configuration.ContainerSize.LARGE_4X.name(), "4xLarge (~40G memory, 10 vCPU)"),
            Pair.make(Configuration.ContainerSize.LARGE_8X.name(), "8xLarge (~80G memory, 20 vCPU)"));
    }
    
    
}
