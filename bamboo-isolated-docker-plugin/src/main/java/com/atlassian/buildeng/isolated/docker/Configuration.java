/*
 * Copyright 2016 Atlassian.
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

import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.runtime.RuntimeTaskDefinition;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;

import javax.annotation.Nonnull;
import java.util.Map;

public final class Configuration {



    private final boolean enabled;
    private final String dockerImage;

    private Configuration(boolean enabled, String image) {
        this.enabled = enabled;
        this.dockerImage = image;
    }

    @Nonnull
    public static Configuration forBuildConfiguration(@Nonnull BuildConfiguration config) {
        boolean enable = config.getBoolean(Constants.ENABLED_FOR_JOB);
        String image = config.getString(Constants.DOCKER_IMAGE);
        return new Configuration(enable, image);
    }

    @Nonnull
    public static Configuration forBuildContext(@Nonnull BuildContext context) {
        Map<String, String> cc = context.getBuildDefinition().getCustomConfiguration();
        return forMap(cc);
    }
    
    @Nonnull
    public static Configuration forDeploymentContext(@Nonnull DeploymentContext context) {
        for (RuntimeTaskDefinition task : context.getRuntimeTaskDefinitions()) {
            if ("com.atlassian.buildeng.bamboo-isolated-docker-plugin:dockertask".equals(task.getPluginKey())) {
                return forTaskConfiguration(task);
            }
        }
        return new Configuration(false, "");
    }
    
    @Nonnull
    public static Configuration forTaskConfiguration(@Nonnull TaskDefinition taskDefinition) {
        Map<String, String> cc = taskDefinition.getConfiguration();
        String value = cc.getOrDefault(Constants.TASK_DOCKER_ENABLE, "false");
        String image = cc.getOrDefault(Constants.TASK_DOCKER_IMAGE, "");
        return new Configuration(Boolean.parseBoolean(value), image);
    }


    public static Configuration forJob(ImmutableJob job) {
        Map<String, String> cc = job.getBuildDefinition().getCustomConfiguration();
        return forMap(cc);
    }
    
    public static Configuration forBuildResultSummary(BuildResultsSummary summary) {
        Map<String, String> cc = summary.getCustomBuildData();
        return forMap(cc);
    }

    @Nonnull
    private static Configuration forMap(@Nonnull Map<String, String> cc) {
        String value = cc.getOrDefault(Constants.ENABLED_FOR_JOB, "false");
        String image = cc.getOrDefault(Constants.DOCKER_IMAGE, "");
        return new Configuration(Boolean.parseBoolean(value), image);
    }


    public boolean isEnabled() {
        return enabled;
    }

    public String getDockerImage() {
        return dockerImage;
    }


}
