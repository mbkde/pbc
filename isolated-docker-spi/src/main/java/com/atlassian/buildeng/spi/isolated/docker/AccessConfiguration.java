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

package com.atlassian.buildeng.spi.isolated.docker;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.Environment;
import static com.atlassian.buildeng.spi.isolated.docker.Configuration.DOCKER_EXTRA_CONTAINERS;
import static com.atlassian.buildeng.spi.isolated.docker.Configuration.DOCKER_IMAGE;
import static com.atlassian.buildeng.spi.isolated.docker.Configuration.DOCKER_IMAGE_SIZE;
import static com.atlassian.buildeng.spi.isolated.docker.Configuration.ENABLED_FOR_JOB;

import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.runtime.RuntimeTaskDefinition;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;

public class AccessConfiguration {

    
    //XXX interplugin dependency
    static final String IMPL_PLUGIN_KEY = "com.atlassian.buildeng.bamboo-isolated-docker-plugin";
    
    @Nonnull
    private static Configuration forMap(@Nonnull Map<String, String> cc) {
        return ConfigurationBuilder.create(cc.getOrDefault(DOCKER_IMAGE, ""))
                    .withEnabled(Boolean.parseBoolean(cc.getOrDefault(ENABLED_FOR_JOB, "false")))
                    .withImageSize(Configuration.ContainerSize.valueOf(cc.getOrDefault(DOCKER_IMAGE_SIZE,
                            Configuration.ContainerSize.REGULAR.name())))
                    .withExtraContainers(
                            ConfigurationPersistence.fromJsonString(cc.getOrDefault(DOCKER_EXTRA_CONTAINERS, "[]")))
                    .build();
    }

    public static Configuration forContext(@Nonnull CommonContext context) {
        if (context instanceof BuildContext) {
            return forBuildContext((BuildContext) context);
        }
        if (context instanceof DeploymentContext) {
            return forDeploymentContext((DeploymentContext) context);
        }
        throw new IllegalStateException("Unknown Common Context subclass:" + context.getClass().getName());
    }

    @Nonnull
    private static Configuration forDeploymentContext(@Nonnull DeploymentContext context) {
        for (RuntimeTaskDefinition task : context.getRuntimeTaskDefinitions()) {
            Map<String, String> map = context.getPluginConfigMap(IMPL_PLUGIN_KEY);
            if (!map.isEmpty()) { 
                //not sure this condition is 100% reliable, when enabling and disabling 
                //the docker tab data will retain some config.
                return forMap(map);
            }
            //XXX interplugin dependency
            if ((IMPL_PLUGIN_KEY + ":dockertask").equals(task.getPluginKey())) {
                return forTaskConfiguration(task);
            }
        }
        return ConfigurationBuilder.create("").withEnabled(false).build();
    }

    @Nonnull
    public static Configuration forBuildConfiguration(@Nonnull BuildConfiguration config) {
        return ConfigurationBuilder.create(config.getString(Configuration.DOCKER_IMAGE))
                .withEnabled(config.getBoolean(Configuration.ENABLED_FOR_JOB))
                .withImageSize(Configuration.ContainerSize.valueOf(config.getString(Configuration.DOCKER_IMAGE_SIZE,
                        Configuration.ContainerSize.REGULAR.name())))
                .withExtraContainers(
                        ConfigurationPersistence.fromJsonString(
                                config.getString(Configuration.DOCKER_EXTRA_CONTAINERS, "[]")))
                .build();
    }

    @Nonnull
    private static Configuration forBuildContext(@Nonnull BuildContext context) {
        Map<String, String> cc = context.getBuildDefinition().getCustomConfiguration();
        return forMap(cc);
    }

    public static Configuration forBuildResultSummary(ResultsSummary summary) {
        Map<String, String> cc = summary.getCustomBuildData();
        return forMap(cc);
    }

    public static Configuration forDeploymentResult(DeploymentResult dr) {
        return forMap(dr.getCustomData());
    }

    @Nonnull
    public static Configuration forTaskConfiguration(@Nonnull TaskDefinition taskDefinition) {
        Map<String, String> cc = taskDefinition.getConfiguration();
        return ConfigurationBuilder.create(cc.getOrDefault(Configuration.TASK_DOCKER_IMAGE, ""))
                .withEnabled(taskDefinition.isEnabled())
                .withImageSize(Configuration.ContainerSize.valueOf(
                        cc.getOrDefault(Configuration.TASK_DOCKER_IMAGE_SIZE,
                                Configuration.ContainerSize.REGULAR.name())))
                .withExtraContainers(
                        ConfigurationPersistence.fromJsonString(cc.getOrDefault(
                                Configuration.TASK_DOCKER_EXTRA_CONTAINERS, "[]")))
                .build();
    }

    public static Configuration forJob(ImmutableJob job) {
        return forBuildDefinition(job.getBuildDefinition());
    }
    
    public static Configuration forBuildDefinition(BuildDefinition buildDefinition) {
        Map<String, String> cc = buildDefinition.getCustomConfiguration();
        return forMap(cc);
    }

    
    public static Configuration forEnvironment(Environment environment, 
            EnvironmentCustomConfigService environmentCustomConfigService) {
        return forMap(environmentCustomConfigService.getEnvironmentPluginConfig(
                environment.getId()).getOrDefault(IMPL_PLUGIN_KEY,
                        Collections.emptyMap()));
    }
}
