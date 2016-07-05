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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public static final String DOCKER_EXTRA_CONTAINERS = "custom.isolated.docker.extraContainers"; 
    //task related equivalents of DOCKER_IMAGE and ENABLED_FOR_DOCKER but plan templates
    // don't like dots in names.
    public static final String TASK_DOCKER_IMAGE = "dockerImage";
    public static final String TASK_DOCKER_IMAGE_SIZE = "dockerImageSize";
    public static final String TASK_DOCKER_EXTRA_CONTAINERS = "extraContainers";
    public static final String TASK_DOCKER_ENABLE = "enabled";
    
    //TODO sort ecs specific but we should eventually get rid of if we
    //introduce  generic support for custom container volumes.
    public static int SIDEKICK_CPU = 40;
    public static int SIDEKICK_MEMORY = 240;
    

    //when storing using bandana/xstream transient means it's not to be serialized
    private final transient boolean enabled;
    private final String dockerImage;
    private final ContainerSize size;
    private final List<ExtraContainer> extraContainers;

    Configuration(boolean enabled, String dockerImage, ContainerSize size, List<ExtraContainer> extraContainers) {
        this.enabled = enabled;
        this.dockerImage = dockerImage;
        this.size = size;
        this.extraContainers = extraContainers;
    }

    @Nonnull
    public static Configuration forBuildConfiguration(@Nonnull BuildConfiguration config) {
        boolean enable = config.getBoolean(ENABLED_FOR_JOB);
        String image = config.getString(DOCKER_IMAGE);
        ContainerSize size = ContainerSize.valueOf(config.getString(DOCKER_IMAGE_SIZE, ContainerSize.REGULAR.name()));
        List<ExtraContainer> extras = fromJsonString(config.getString(DOCKER_EXTRA_CONTAINERS, "[]"));
        return new Configuration(enable, image, size, extras);
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
        return new Configuration(false, "", ContainerSize.REGULAR, Collections.emptyList());
    }
    
    public static Configuration forDeploymentResult(DeploymentResult dr) {
        return forMap(dr.getCustomData());
    }
    
    
    @Nonnull
    public static Configuration forTaskConfiguration(@Nonnull TaskDefinition taskDefinition) {
        Map<String, String> cc = taskDefinition.getConfiguration();
        String value = cc.getOrDefault(TASK_DOCKER_ENABLE, "false");
        String image = cc.getOrDefault(TASK_DOCKER_IMAGE, "");
        ContainerSize size = ContainerSize.valueOf(cc.getOrDefault(TASK_DOCKER_IMAGE_SIZE, ContainerSize.REGULAR.name()));
        List<ExtraContainer> extras = fromJsonString(cc.getOrDefault(TASK_DOCKER_EXTRA_CONTAINERS, "[]"));
        return new Configuration(Boolean.parseBoolean(value), image, size, extras);
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
        List<ExtraContainer> extras = fromJsonString(cc.getOrDefault(DOCKER_EXTRA_CONTAINERS, "[]"));
        return new Configuration(Boolean.parseBoolean(value), image, size, extras);
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
    
    public int getCPUTotal() {
        return size.cpu() + SIDEKICK_CPU + extraContainers.stream().mapToInt((ExtraContainer value) -> value.getExtraSize().cpu).sum();
    }
    
    public int getMemoryTotal() {
        return size.memory() + SIDEKICK_MEMORY + extraContainers.stream().mapToInt((ExtraContainer value) -> value.getExtraSize().memory).sum();
    }

    public List<ExtraContainer> getExtraContainers() {
        return extraContainers;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.dockerImage);
        hash = 79 * hash + Objects.hashCode(this.size);
        hash = 79 * hash + Objects.hashCode(this.extraContainers);
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
        if (this.size != other.size) {
            return false;
        }
        return Objects.equals(this.extraContainers, other.extraContainers);
    }

    public void copyTo(Map<String, ? super String> storageMap) {
        storageMap.put(Configuration.ENABLED_FOR_JOB, "" + isEnabled());
        storageMap.put(Configuration.DOCKER_IMAGE, getDockerImage());
        storageMap.put(Configuration.DOCKER_IMAGE_SIZE, getSize().name());
        storageMap.put(Configuration.DOCKER_EXTRA_CONTAINERS, toJson(getExtraContainers()).toString());
    }
    
    public static void removeFrom(Map<String, ? super String> storageMap) {
        storageMap.remove(Configuration.ENABLED_FOR_JOB);
        storageMap.remove(Configuration.DOCKER_IMAGE);
        storageMap.remove(Configuration.DOCKER_IMAGE_SIZE);
        storageMap.remove(Configuration.DOCKER_EXTRA_CONTAINERS);
    }

    public static JsonArray toJson(List<ExtraContainer> extraContainers) {
        JsonArray arr = new JsonArray();
        extraContainers.forEach((ExtraContainer t) -> {
            arr.add(t.toJson());
        });
        return arr;
    }
    
    private static List<ExtraContainer> fromJsonString(String source) {
        List<ExtraContainer> toRet = new ArrayList<>();
        try {
            JsonElement obj = new JsonParser().parse(source);
            if (obj.isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray();
                arr.forEach((JsonElement t) -> {
                    if (t.isJsonObject()) {
                        ExtraContainer cont = from(t.getAsJsonObject());
                        if (cont != null) {
                            toRet.add(cont);
                        }
                    }
                });
            }
        } catch (JsonParseException ex) {
            
        }
        return toRet;
        
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
    

    public static class ExtraContainer {
        String name;
        String image;
        ExtraContainerSize extraSize = ExtraContainerSize.REGULAR;

        public ExtraContainer(String name, String image, ExtraContainerSize extraSize) {
            this.name = name;
            this.image = image;
            this.extraSize = extraSize;
        }

        public String getName() {
            return name;
        }

        public String getImage() {
            return image;
        }

        public ExtraContainerSize getExtraSize() {
            return extraSize;
        }
        
        

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.name);
            hash = 59 * hash + Objects.hashCode(this.image);
            hash = 59 * hash + Objects.hashCode(this.extraSize);
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
            final ExtraContainer other = (ExtraContainer) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.image, other.image)) {
                return false;
            }
            return this.extraSize == other.extraSize;
        }

        private JsonObject toJson() {
            JsonObject el = new JsonObject();
            el.addProperty("name", name);
            el.addProperty("image", image);
            el.addProperty("size", extraSize.name());
            return el;
        }
    }
    
    public static ExtraContainer from(JsonObject obj) {
        if (obj.has("name") && obj.has("image") && obj.has("size")) {
            String name = obj.getAsJsonPrimitive("name").getAsString();
            String image = obj.getAsJsonPrimitive("image").getAsString();
            String size = obj.getAsJsonPrimitive("size").getAsString();
            ExtraContainerSize s;
            try {
                s = ExtraContainerSize.valueOf(size);
            } catch (IllegalArgumentException x) {
                s = ExtraContainerSize.REGULAR;
            }
            return new ExtraContainer(name, image, s);
        } 
        return null;
    }
    
    
    public static enum ExtraContainerSize {
        REGULAR(500, 2000),
        SMALL(250,1000);
        
        private final int cpu;
        private final int memory;

        private ExtraContainerSize(int cpu, int memory) {
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
