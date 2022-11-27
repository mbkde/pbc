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

package com.atlassian.buildeng.isolated.docker.lifecycle;

import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.CustomBuildProcessorServer;
import com.atlassian.bamboo.specs.api.builders.pbc.EnvVar;
import com.atlassian.bamboo.specs.api.builders.pbc.ExtraContainer;
import com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForJob;
import com.atlassian.bamboo.specs.api.exceptions.PropertiesValidationException;
import com.atlassian.bamboo.specs.api.model.pbc.EnvProperties;
import com.atlassian.bamboo.specs.api.model.pbc.ExtraContainerProperties;
import com.atlassian.bamboo.specs.api.model.pbc.PerBuildContainerForJobProperties;
import com.atlassian.bamboo.specs.api.validators.common.ValidationProblem;
import com.atlassian.bamboo.specs.yaml.Node;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BaseConfigurablePlugin;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.CurrentlyBuilding;
import com.atlassian.bamboo.v2.build.ImportExportAwarePlugin;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.isolated.docker.handler.CustomEnvironmentConfigExporterImpl;
import com.atlassian.buildeng.isolated.docker.yaml.YamlConfigParser;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the purpose of the class is to do cleanup if the normal way of killing the agent
 * after the job completion fails.
 * The one use case we know about is BUILDENG-10514 where the agent fails to run any
 * pre or post actions on the agent if artifact download fails.
 */
public class BuildProcessorServerImpl extends BaseConfigurablePlugin implements CustomBuildProcessorServer,
        ImportExportAwarePlugin<PerBuildContainerForJob, PerBuildContainerForJobProperties> {
    private static final Logger LOG = LoggerFactory.getLogger(BuildProcessorServerImpl.class);
    public static final String CAPABILITY = Capability.SYSTEM_PREFIX + ".isolated.docker";

    private BuildContext buildContext;
    private AgentRemovals agentRemovals;
    private BuildExecutionManager buildExecutionManager;
    private Validator validator;

    // setters here for components, otherwise the parent fields don't get injected.
    @Inject
    public BuildProcessorServerImpl(AgentRemovals agentRemovals,
            BuildExecutionManager buildExecutionManager,
            Validator validator) {
        this.agentRemovals = agentRemovals;
        this.buildExecutionManager = buildExecutionManager;
        this.validator = validator;
    }

    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() {
        Configuration conf = AccessConfiguration.forContext(buildContext);
        CurrentBuildResult buildResult = buildContext.getBuildResult();

        // in some cases the agent cannot kill itself (eg. when artifact subscription fails
        // and our StopDockerAgentBuildProcessor is not executed. absence of the marker property
        // tells us that we didn't run on agent
        if (conf.isEnabled() && null == buildResult.getCustomBuildData().get(Constants.RESULT_AGENT_KILLED_ITSELF)) {
            CurrentlyBuilding building = buildExecutionManager.getCurrentlyBuildingByBuildResult(buildContext);
            Long agentId = null;
            if (building != null) {
                agentId = building.getBuildAgentId();
            }
            if (building != null && agentId != null) {
                agentRemovals.stopAgentRemotely(agentId);
                agentRemovals.removeAgent(agentId);
                LOG.info("Build result {} not shutting down normally, killing agent {} explicitly.",
                        buildContext.getPlanResultKey().getKey(),
                        agentId);
            } else {
                LOG.warn("Agent for {} not found. Cannot stop the agent.", buildContext.getPlanResultKey().getKey());
            }

        }
        return buildContext;
    }

    @NotNull
    @Override
    public Set<String> getConfigurationKeys() {
        return new HashSet<>(Arrays.asList(Configuration.ENABLED_FOR_JOB,
                Configuration.DOCKER_IMAGE,
                Configuration.DOCKER_IMAGE_SIZE,
                Configuration.DOCKER_AWS_ROLE,
                Configuration.DOCKER_EXTRA_CONTAINERS,
                Configuration.DOCKER_ARCHITECTURE));
    }

    /**
     * {@link com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForEnvironment#architecture(String
     * architecture) }
     * The usage of the .architecture(String arch) builder method is discouraged due to it being error prone.
     * Therefore, in the PBC specs extension, we provide an enum to alleviate this and place a deprecated
     * annotation on the string builder method. However, the usage of this method is mandatory here,
     * in order to support architectures which may not be specified in the Architecture enum.
     */
    @NotNull
    @Override
    public PerBuildContainerForJob toSpecsEntity(HierarchicalConfiguration buildConfiguration) {
        String enabled = buildConfiguration.getString(Configuration.ENABLED_FOR_JOB, "false");
        Map<String, String> cc = new HashMap<>();
        cc.put(Configuration.ENABLED_FOR_JOB, enabled);
        String image = buildConfiguration.getString(Configuration.DOCKER_IMAGE);
        if (image != null) {
            cc.put(Configuration.DOCKER_IMAGE, image);
        }
        String size = buildConfiguration.getString(Configuration.DOCKER_IMAGE_SIZE);
        if (size != null) {
            cc.put(Configuration.DOCKER_IMAGE_SIZE, size);
        }
        String extra = buildConfiguration.getString(Configuration.DOCKER_EXTRA_CONTAINERS);
        if (extra != null) {
            cc.put(Configuration.DOCKER_EXTRA_CONTAINERS, extra);
        }
        String role = buildConfiguration.getString(Configuration.DOCKER_AWS_ROLE);
        if (role != null) {
            cc.put(Configuration.DOCKER_AWS_ROLE, role);
        }
        String architecture = buildConfiguration.getString(Configuration.DOCKER_ARCHITECTURE);
        if (architecture != null) {
            cc.put(Configuration.DOCKER_ARCHITECTURE, architecture);
        }
        Configuration c = AccessConfiguration.forMap(cc);
        return new PerBuildContainerForJob()
                .enabled(c.isEnabled())
                .image(c.getDockerImage())
                .size(c.getSize().name())
                .awsRole(c.getAwsRole())
                .architecture(c.getArchitecture())
                .extraContainers(c
                        .getExtraContainers()
                        .stream()
                        .map(getExtraContainerExtraContainerFunction())
                        .collect(Collectors.toList()));
    }

    /**
     * Converter for extra container.
     */
    @NotNull
    public static Function<Configuration.ExtraContainer, ExtraContainer> getExtraContainerExtraContainerFunction() {
        return (Configuration.ExtraContainer t) -> new ExtraContainer()
                .name(t.getName())
                .image(t.getImage())
                .size(t.getExtraSize().name())
                .commands(t.getCommands())
                .envVariables(t
                        .getEnvVariables()
                        .stream()
                        .map((Configuration.EnvVariable t2) -> new EnvVar(t2.getName(), t2.getValue()))
                        .collect(Collectors.toList()));
    }

    @Override
    public void addToBuildConfiguration(PerBuildContainerForJobProperties specsProperties,
            @NotNull HierarchicalConfiguration buildConfiguration) {
        if (specsProperties.isEnabled()) {
            // apparently unlike in CustomEnvironmentConfigPluginExporter there is no explicit validation callback
            // and the infra is not calling it either. Doing it here for the lack of a better place.
            specsProperties.validate();
            ErrorCollection errorCollection = new SimpleErrorCollection();
            validator.validate(specsProperties.getImage(),
                    specsProperties.getSize(),
                    specsProperties.getAwsRole(),
                    specsProperties.getArchitecture(),
                    toJsonString(specsProperties.getExtraContainers()),
                    errorCollection,
                    false);
            if (errorCollection.hasAnyErrors()) {
                throw new PropertiesValidationException(errorCollection
                        .getAllErrorMessages()
                        .stream()
                        .map(ValidationProblem::new)
                        .collect(Collectors.toList()));
            }
        }
        buildConfiguration.setProperty(Configuration.ENABLED_FOR_JOB, specsProperties.isEnabled());
        buildConfiguration.setProperty(Configuration.DOCKER_IMAGE, specsProperties.getImage());
        buildConfiguration.setProperty(Configuration.DOCKER_IMAGE_SIZE, specsProperties.getSize());
        buildConfiguration.setProperty(Configuration.DOCKER_AWS_ROLE, specsProperties.getAwsRole());
        buildConfiguration.setProperty(Configuration.DOCKER_ARCHITECTURE, specsProperties.getArchitecture());
        buildConfiguration.setProperty(Configuration.DOCKER_EXTRA_CONTAINERS,
                toJsonString(specsProperties.getExtraContainers()));
    }

    /**
     * {@link com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForEnvironment#architecture(String
     * architecture) }
     * The usage of the .architecture(String arch) builder method is discouraged due to it being error prone.
     * Therefore, in the PBC specs extension, we provide an enum to alleviate this and place a deprecated
     * annotation on the string builder method. However, the usage of this method is mandatory here,
     * in order to support architectures which may not be specified in the Architecture enum.
     */
    @Nullable
    @Override
    public PerBuildContainerForJob fromYaml(@NotNull Node node) {
        YamlConfigParser parser = new YamlConfigParser();
        Configuration config = parser.parse(node);
        if (config == null) {
            return null;
        } else {
            return new PerBuildContainerForJob()
                    .enabled(config.isEnabled())
                    .image(config.getDockerImage())
                    .size(config.getSize().name())
                    .awsRole(config.getAwsRole())
                    .architecture(config.getArchitecture())
                    .extraContainers(config
                            .getExtraContainers()
                            .stream()
                            .map(BuildProcessorServerImpl.getExtraContainerExtraContainerFunction())
                            .collect(Collectors.toList()));
        }
    }

    @Nullable
    @Override
    public Node toYaml(@NotNull PerBuildContainerForJobProperties specsProperties) {
        final YamlConfigParser parser = new YamlConfigParser();
        return parser.toYaml(toConfig(specsProperties));
    }

    /**
     * Convert list of ExtraContainerProperties definitions into a json string.
     */
    public static String toJsonString(List<ExtraContainerProperties> extraContainers) {
        return ConfigurationPersistence.toJson(extraContainers.stream().map((ExtraContainerProperties t) -> {
            Configuration.ExtraContainer ec = new Configuration.ExtraContainer(t.getName(),
                    t.getImage(),
                    Configuration.ExtraContainerSize.valueOf(t.getSize()));
            ec.setCommands(t.getCommands());
            ec.setEnvVariables(t
                    .getEnvironments()
                    .stream()
                    .map((EnvProperties e) -> new Configuration.EnvVariable(e.getKey(), e.getValue()))
                    .collect(Collectors.toList()));
            return ec;
        }).collect(Collectors.toList())).toString();
    }

    private Configuration toConfig(PerBuildContainerForJobProperties specsProperties) {
        ConfigurationBuilder builder = ConfigurationBuilder.create(specsProperties.getImage());
        builder.withImageSize(Configuration.ContainerSize.valueOf(specsProperties.getSize()));
        if (StringUtils.isNotBlank(specsProperties.getAwsRole())) {
            builder.withAwsRole(specsProperties.getAwsRole());
        }
        if (StringUtils.isNotBlank(specsProperties.getArchitecture())) {
            builder.withArchitecture(specsProperties.getArchitecture());
        }
        if (specsProperties.getExtraContainers() != null) {
            specsProperties.getExtraContainers().forEach(container -> {
                CustomEnvironmentConfigExporterImpl.convertExtraContainer(builder, container);
            });
        }
        return builder.build();
    }
}
