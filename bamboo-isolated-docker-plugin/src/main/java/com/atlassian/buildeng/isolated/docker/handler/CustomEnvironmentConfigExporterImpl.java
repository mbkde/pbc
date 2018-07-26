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
import com.atlassian.bamboo.specs.api.model.deployment.configuration.AnyPluginConfigurationProperties;
import com.atlassian.bamboo.specs.api.model.deployment.configuration.EnvironmentPluginConfigurationProperties;
import com.atlassian.bamboo.specs.api.model.pbc.PerBuildContainerForEnvironmentProperties;
import com.atlassian.bamboo.specs.api.validators.common.ValidationProblem;
import com.atlassian.bamboo.task.export.TaskValidationContext;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.isolated.docker.lifecycle.BuildProcessorServerImpl;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomEnvironmentConfigExporterImpl implements CustomEnvironmentConfigPluginExporter {

    // these things can never ever change value, because they end up as part of export
    static final String ENV_CONFIG_MODULE_KEY = 
            "com.atlassian.buildeng.bamboo-isolated-docker-plugin:pbcEnvironment";

    @Override
    public EnvironmentPluginConfiguration toSpecsEntity(Map<String, String> map) {
        Configuration config = AccessConfiguration.forMap(map);
        return new PerBuildContainerForEnvironment()
                .enabled(config.isEnabled())
                .image(config.getDockerImage())
                .size(config.getSize().name())
                .extraContainers(config.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> {
                    return new ExtraContainer()
                        .name(t.getName())
                        .image(t.getImage())
                        .size(t.getExtraSize().name())
                        .commands(t.getCommands())
                        .envVariables(t.getEnvVariables().stream().map((Configuration.EnvVariable t2) -> {
                            return new EnvVar(t2.getName(), t2.getValue());
                        })
                        .collect(Collectors.toList()));
                }).collect(Collectors.toList()));
    }

    @Override
    public Map<String, String> toConfiguration(EnvironmentPluginConfigurationProperties epcp) {
        final AnyPluginConfigurationProperties any = Narrow.downTo(epcp, AnyPluginConfigurationProperties.class);
        if (any != null) {
            return any.getConfiguration();
        }
        final PerBuildContainerForEnvironmentProperties custom =  
                Narrow.downTo(epcp, PerBuildContainerForEnvironmentProperties.class);
        if (custom != null) {
            Map<String, String> toRet = new HashMap();
            toRet.put(Configuration.ENABLED_FOR_JOB, "" + custom.isEnabled());
            toRet.put(Configuration.DOCKER_IMAGE, custom.getImage());
            toRet.put(Configuration.DOCKER_IMAGE_SIZE, custom.getSize());
            toRet.put(Configuration.DOCKER_EXTRA_CONTAINERS, 
                    BuildProcessorServerImpl.toJsonString(custom.getExtraContainers()));
            return toRet;
        }
        throw new IllegalStateException("Don't know how to import configuration of type: " + epcp.getClass().getName());
    }

    @Override
    public List<ValidationProblem> validate(TaskValidationContext tvc, EnvironmentPluginConfigurationProperties epcp) {
        final AnyPluginConfigurationProperties any = Narrow.downTo(epcp, AnyPluginConfigurationProperties.class);
        if (any != null) {
            String enabled = any.getConfiguration().get(Configuration.ENABLED_FOR_JOB);
            String size = any.getConfiguration().get(Configuration.DOCKER_IMAGE_SIZE);
            String image = any.getConfiguration().get(Configuration.DOCKER_IMAGE);
            String extraCont = any.getConfiguration().get(Configuration.DOCKER_EXTRA_CONTAINERS);
            ErrorCollection coll = new SimpleErrorCollection();
                    
            Validator.validate(image, size, extraCont, coll, false);
            return coll.getAllErrorMessages().stream()
                    .map((String t) -> new ValidationProblem(t))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
    
}
