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

package com.atlassian.buildeng.isolated.docker.deployment;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskRequirementSupport;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.isolated.docker.handler.DockerHandlerImpl;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.struts.TextProvider;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RequirementTaskConfigurator extends AbstractTaskConfigurator implements TaskRequirementSupport {

    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = LoggerFactory.getLogger(RequirementTaskConfigurator.class);
    private final TextProvider textProvider;
    private final DockerHandlerImpl dockerHandler;

    private RequirementTaskConfigurator(TextProvider textProvider, DockerHandlerImpl dockerHandler) {
        this.textProvider = textProvider;
        this.dockerHandler = dockerHandler;
    }

    @NotNull
    @Override
    public Map<String, String> generateTaskConfigMap(@NotNull ActionParametersMap params, 
            @Nullable TaskDefinition previousTaskDefinition) {
        Map<String, String> configMap = super.generateTaskConfigMap(params, previousTaskDefinition);
        configMap.put(Configuration.TASK_DOCKER_IMAGE, params.getString(Configuration.TASK_DOCKER_IMAGE));
        configMap.put(Configuration.TASK_DOCKER_ARCHITECTURE, params.getString(Configuration.TASK_DOCKER_ARCHITECTURE));
        configMap.put(Configuration.TASK_DOCKER_IMAGE_SIZE, params.getString(Configuration.TASK_DOCKER_IMAGE_SIZE));
        configMap.put(Configuration.TASK_DOCKER_EXTRA_CONTAINERS, 
                params.getString(Configuration.TASK_DOCKER_EXTRA_CONTAINERS));
        return configMap;
    }

    @Override
    public void populateContextForEdit(@NotNull Map<String, Object> context, @NotNull TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        context.putAll(taskDefinition.getConfiguration());
        context.put(Configuration.TASK_DOCKER_IMAGE, 
                taskDefinition.getConfiguration().get(Configuration.TASK_DOCKER_IMAGE));
        context.put("imageSizes", DockerHandlerImpl.getImageSizes());
        context.put(Configuration.TASK_DOCKER_IMAGE_SIZE, 
                taskDefinition.getConfiguration().get(Configuration.TASK_DOCKER_IMAGE_SIZE));
        context.put("architectureList", dockerHandler.getArchitectures());
        context.put(Configuration.DOCKER_ARCHITECTURE, taskDefinition.getConfiguration().get(Configuration.DOCKER_ARCHITECTURE));
        context.put(Configuration.TASK_DOCKER_EXTRA_CONTAINERS, 
                taskDefinition.getConfiguration().get(Configuration.TASK_DOCKER_EXTRA_CONTAINERS));
    }

    @Override
    public void populateContextForCreate(Map<String, Object> context) {
        super.populateContextForCreate(context);
        context.put("imageSizes", DockerHandlerImpl.getImageSizes());
        context.put("architectureList", dockerHandler.getArchitectures());
        context.put(Configuration.TASK_DOCKER_IMAGE_SIZE, Configuration.ContainerSize.REGULAR);
    }

    @Override
    public void validate(@NotNull ActionParametersMap params, @NotNull ErrorCollection errorCollection) {
        super.validate(params, errorCollection);
        
        String image = params.getString(Configuration.TASK_DOCKER_IMAGE);
        String extraCont = params.getString(Configuration.TASK_DOCKER_EXTRA_CONTAINERS);
        String size = params.getString(Configuration.TASK_DOCKER_IMAGE_SIZE);
        String role = params.getString(Configuration.TASK_DOCKER_AWS_ROLE);
        String architecture = params.getString(Configuration.TASK_DOCKER_ARCHITECTURE);
        Validator.validate(image, size, role, architecture, extraCont, errorCollection, true);
    }
    

    // TODO eventually remove once we are sure noone is using the capability anymore.

    @NotNull
    @Override
    public Set<Requirement> calculateRequirements(@NotNull TaskDefinition taskDefinition) {
        Set<Requirement> requirementSet = Sets.newHashSet();
        Configuration config = AccessConfiguration.forTaskConfiguration(taskDefinition);
        if (config.isEnabled()) {
            requirementSet.add(new RequirementImpl(Constants.CAPABILITY_RESULT, true, ".*", true));
        }
        return requirementSet;
    }

}
