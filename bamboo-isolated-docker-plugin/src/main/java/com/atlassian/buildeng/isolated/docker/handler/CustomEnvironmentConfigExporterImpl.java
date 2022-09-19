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

import com.atlassian.bamboo.deployments.configuration.CustomEnvironmentConfigPluginExporter;
import com.atlassian.bamboo.specs.api.builders.deployment.configuration.EnvironmentPluginConfiguration;
import com.atlassian.bamboo.specs.api.builders.pbc.EnvVar;
import com.atlassian.bamboo.specs.api.builders.pbc.ExtraContainer;
import com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForEnvironment;
import com.atlassian.bamboo.specs.api.exceptions.PropertiesValidationException;
import com.atlassian.bamboo.specs.api.model.deployment.configuration.AnyPluginConfigurationProperties;
import com.atlassian.bamboo.specs.api.model.deployment.configuration.EnvironmentPluginConfigurationProperties;
import com.atlassian.bamboo.specs.api.model.pbc.ExtraContainerProperties;
import com.atlassian.bamboo.specs.api.model.pbc.PerBuildContainerForEnvironmentProperties;
import com.atlassian.bamboo.specs.api.validators.common.ValidationProblem;
import com.atlassian.bamboo.specs.yaml.Node;
import com.atlassian.bamboo.task.export.TaskValidationContext;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.isolated.docker.lifecycle.BuildProcessorServerImpl;
import com.atlassian.buildeng.isolated.docker.yaml.YamlConfigParser;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CustomEnvironmentConfigExporterImpl implements CustomEnvironmentConfigPluginExporter {

    private final Validator validator;

    // these things can never ever change value, because they end up as part of export
    static final String ENV_CONFIG_MODULE_KEY =
            "com.atlassian.buildeng.bamboo-isolated-docker-plugin:pbcEnvironment";

    public CustomEnvironmentConfigExporterImpl(Validator validator) {
        this.validator = validator;
    }

    @NotNull
    @Override
    public EnvironmentPluginConfiguration toSpecsEntity(@NotNull Map<String, String> map) {
        Configuration config = AccessConfiguration.forMap(map);
        return new PerBuildContainerForEnvironment()
                .enabled(config.isEnabled())
                .image(config.getDockerImage())
                .size(config.getSize().name())
                .awsRole(config.getAwsRole())
                .architecture(config.getArchitecture())
                .extraContainers(config.getExtraContainers().stream()
                        .map((Configuration.ExtraContainer t) ->
                                new ExtraContainer()
                                        .name(t.getName())
                                        .image(t.getImage())
                                        .size(t.getExtraSize().name())
                                        .commands(t.getCommands())
                                        .envVariables(t.getEnvVariables().stream()
                                                .map((Configuration.EnvVariable t2)
                                                        -> new EnvVar(t2.getName(), t2.getValue()))
                                                .collect(Collectors.toList())))
                        .collect(Collectors.toList()));
    }

    @NotNull
    @Override
    public Map<String, String> toConfiguration(@NotNull EnvironmentPluginConfigurationProperties epcp) {
        final AnyPluginConfigurationProperties any = Narrow.downTo(epcp, AnyPluginConfigurationProperties.class);
        if (any != null) {
            return any.getConfiguration();
        }
        final PerBuildContainerForEnvironmentProperties custom =
                Narrow.downTo(epcp, PerBuildContainerForEnvironmentProperties.class);
        if (custom != null) {
            Map<String, String> toRet = new HashMap<>();
            toRet.put(Configuration.ENABLED_FOR_JOB, "" + custom.isEnabled());
            toRet.put(Configuration.DOCKER_IMAGE, custom.getImage());
            toRet.put(Configuration.DOCKER_IMAGE_SIZE, custom.getSize());
            toRet.put(Configuration.DOCKER_AWS_ROLE, custom.getAwsRole());
            toRet.put(Configuration.DOCKER_ARCHITECTURE, custom.getArchitecture());
            toRet.put(Configuration.DOCKER_EXTRA_CONTAINERS,
                    BuildProcessorServerImpl.toJsonString(custom.getExtraContainers()));
            return toRet;
        }
        throw new IllegalStateException("Don't know how to import configuration of type: " + epcp.getClass().getName());
    }

    @NotNull
    @Override
    public List<ValidationProblem> validate(@NotNull TaskValidationContext tvc,
                                            @NotNull EnvironmentPluginConfigurationProperties epcp) {
        final AnyPluginConfigurationProperties any = Narrow.downTo(epcp, AnyPluginConfigurationProperties.class);
        if (any != null) {
            String enabled = any.getConfiguration().get(Configuration.ENABLED_FOR_JOB);
            String size = any.getConfiguration().get(Configuration.DOCKER_IMAGE_SIZE);
            String image = any.getConfiguration().get(Configuration.DOCKER_IMAGE);
            String extraCont = any.getConfiguration().get(Configuration.DOCKER_EXTRA_CONTAINERS);
            String awsRole = any.getConfiguration().get(Configuration.DOCKER_AWS_ROLE);
            String architecture = any.getConfiguration().get(Configuration.DOCKER_ARCHITECTURE);
            if (StringUtils.isBlank(awsRole)) {
                awsRole = null;
            }
            if (StringUtils.isBlank(architecture)) {
                architecture = null;
            }
            ErrorCollection coll = new SimpleErrorCollection();
            if (Boolean.parseBoolean(enabled)) {
                validator.validate(image, size, awsRole, architecture, extraCont, coll, false);
                return coll.getAllErrorMessages().stream()
                        .map(ValidationProblem::new)
                        .collect(Collectors.toList());
            }
        }
        final PerBuildContainerForEnvironmentProperties pbc =
                Narrow.downTo(epcp, PerBuildContainerForEnvironmentProperties.class);
        if (pbc != null && pbc.isEnabled()) {
            ErrorCollection coll = new SimpleErrorCollection();
            validator.validate(pbc.getImage(), pbc.getSize(), pbc.getAwsRole(),
                    pbc.getArchitecture(), BuildProcessorServerImpl.toJsonString(pbc.getExtraContainers()), coll, false);
            return coll.getAllErrorMessages().stream()
                    .map(ValidationProblem::new)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * {@link com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForEnvironment#architecture(String architecture) }
     * The usage of the .architecture(String arch) builder method is discouraged due to it being error prone.
     * Therefore, in the PBC specs extension, we provide an enum to alleviate this and place a deprecated
     * annotation on the string builder method. However, the usage of this method is mandatory here,
     * in order to support architectures which may not be specified in the Architecture enum.
     */
    @Override
    public PerBuildContainerForEnvironment fromYaml(@NotNull Node node) throws PropertiesValidationException {
        YamlConfigParser parser = new YamlConfigParser();
        Configuration config = parser.parse(node);
        if (config == null) {
            return null;
        } else {
            return new PerBuildContainerForEnvironment()
                    .enabled(config.isEnabled())
                    .image(config.getDockerImage())
                    .size(config.getSize().name())
                    .awsRole(config.getAwsRole())
                    .architecture(config.getArchitecture())
                    .extraContainers(config.getExtraContainers().stream()
                            .map(BuildProcessorServerImpl.getExtraContainerExtraContainerFunction())
                            .collect(Collectors.toList()));
        }
    }

    @Nullable
    @Override
    public Node toYaml(@NotNull EnvironmentPluginConfigurationProperties specsProperties) {
        YamlConfigParser parser = new YamlConfigParser();
        return parser.toYaml(toConfig((PerBuildContainerForEnvironmentProperties) specsProperties));
    }

    private Configuration toConfig(PerBuildContainerForEnvironmentProperties specsProperties) {
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
                convertExtraContainer(builder, container);
            });
        }
        return builder.build();
    }

    /**
     * Convert extra container.
     *
     * @param builder   builder
     * @param container container
     */
    public static void convertExtraContainer(ConfigurationBuilder builder, ExtraContainerProperties container) {
        Configuration.ExtraContainer extra = new Configuration.ExtraContainer(container.getName(),
                container.getImage(),
                Configuration.ExtraContainerSize.valueOf(container.getSize()));
        extra.setCommands(container.getCommands());
        if (container.getEnvironments() != null) {
            extra.setEnvVariables(
                    container.getEnvironments().stream()
                            .map(var -> new Configuration.EnvVariable(var.getKey(), var.getValue()))
                            .collect(Collectors.toList())
            );
        }
        builder.withExtraContainer(extra);
    }
}
