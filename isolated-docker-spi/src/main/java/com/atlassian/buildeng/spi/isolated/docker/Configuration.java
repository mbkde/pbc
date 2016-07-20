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

package com.atlassian.buildeng.spi.isolated.docker;

import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.runtime.RuntimeTaskDefinition;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

public final class Configuration {
    
    //message to future me:
    // never ever attempt nested values here. eg.
    //custom.isolated.docker.image and custom.isolated.docker.image.size 
    // the way bamboo serializes these will cause the parent to get additional trailing whitespace
    // and you get mad trying to figure out why.
    public static final String ENABLED_FOR_JOB = "custom.isolated.docker.enabled"; 
    public static final String DOCKER_IMAGE = "custom.isolated.docker.image"; 
    public static final String DOCKER_IMAGE_SIZE = "custom.isolated.docker.imageSize"; 
    //task related equivalents of DOCKER_IMAGE and ENABLED_FOR_DOCKER but plan templates
    // don't like dots in names.
    public static final String TASK_DOCKER_IMAGE = "dockerImage";
    public static final String TASK_DOCKER_IMAGE_SIZE = "dockerImageSize";
    public static final String TASK_DOCKER_ENABLE = "enabled";

    //when storing using bandana/xstream transient means it's not to be serialized
    private final transient boolean enabled;
    private final String dockerImage;
    private final ContainerSize size;

    Configuration(boolean enabled, String dockerImage, ContainerSize size) {
        this.enabled = enabled;
        this.dockerImage = dockerImage;
        this.size = size;
    }

    @Nonnull
    public static Configuration forBuildConfiguration(@Nonnull BuildConfiguration config) {
        boolean enable = config.getBoolean(ENABLED_FOR_JOB);
        String image = config.getString(DOCKER_IMAGE);
        ContainerSize size = ContainerSize.valueOf(config.getString(DOCKER_IMAGE_SIZE, ContainerSize.REGULAR.name()));
        return new Configuration(enable, image, size);
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
    private static Configuration forBuildContext(@Nonnull BuildContext context) {
        Map<String, String> cc = context.getBuildDefinition().getCustomConfiguration();
        return forMap(cc);
    }
    
    @Nonnull
    private static Configuration forDeploymentContext(@Nonnull DeploymentContext context) {
        for (RuntimeTaskDefinition task : context.getRuntimeTaskDefinitions()) {
            //XXX interplugin dependency
            if ("com.atlassian.buildeng.bamboo-isolated-docker-plugin:dockertask".equals(task.getPluginKey())) {
                return forTaskConfiguration(task);
            }
        }
        return new Configuration(false, "", ContainerSize.REGULAR);
    }
    
    public static Configuration forDeploymentResult(DeploymentResult dr) {
        return forMap(dr.getCustomData());
    }
    
    
    @Nonnull
    public static Configuration forTaskConfiguration(@Nonnull TaskDefinition taskDefinition) {
        Map<String, String> cc = taskDefinition.getConfiguration();
        String image = cc.getOrDefault(TASK_DOCKER_IMAGE, "");
        ContainerSize size = ContainerSize.valueOf(cc.getOrDefault(TASK_DOCKER_IMAGE_SIZE, ContainerSize.REGULAR.name()));
        return new Configuration(taskDefinition.isEnabled(), image, size);
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
        String value = cc.getOrDefault(ENABLED_FOR_JOB, "false");
        String image = cc.getOrDefault(DOCKER_IMAGE, "");
        ContainerSize size = ContainerSize.valueOf(cc.getOrDefault(DOCKER_IMAGE_SIZE, ContainerSize.REGULAR.name()));
        return new Configuration(Boolean.parseBoolean(value), image, size);
    }


    public boolean isEnabled() {
        return enabled;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public ContainerSize getSize() {
        return size;
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.dockerImage);
        hash = 23 * hash + Objects.hashCode(this.size);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Configuration other = (Configuration) obj;
        if (!Objects.equals(this.dockerImage, other.dockerImage)) {
            return false;
        }
        return this.size == other.size;
    }

    public void copyTo(Map<String, ? super String> storageMap) {
        storageMap.put(Configuration.ENABLED_FOR_JOB, "" + isEnabled());
        storageMap.put(Configuration.DOCKER_IMAGE, getDockerImage());
        storageMap.put(Configuration.DOCKER_IMAGE_SIZE, getSize().name());
    }
    
    public static void removeFrom(Map<String, ? super String> storageMap) {
        storageMap.remove(Configuration.ENABLED_FOR_JOB);
        storageMap.remove(Configuration.DOCKER_IMAGE);
        storageMap.remove(Configuration.DOCKER_IMAGE_SIZE);
    }
    

    public static enum ContainerSize {
        REGULAR(2000, 7800),
        SMALL(1000,3900);
        
        private final int cpu;
        private final int memory;

        private ContainerSize(int cpu, int memory) {
            this.cpu = cpu;
            this.memory = memory;
        }

        public int cpu() {
            return cpu;
        }

        public int memory() {
            return memory;
        }
        
    }
    


}
