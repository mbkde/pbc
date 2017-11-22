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

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

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

    public static int DOCKER_MINIMUM_MEMORY = 4;

    // a system property containing a map of Docker registries to replace other Docker registries when used in
    // image names. The actual format is a comma separated list of registries, where every other registry
    // is the registry that should replace the preceding registry.
    // For example, "original.com,replacement.com,another.com,anothersreplacement.com"
    private static final String PROPERTY_DOCKER_REGISTRY_MAPPING = "pbc.docker.registry.map";

    //when storing using bandana/xstream transient means it's not to be serialized
    private final transient boolean enabled;
    private String dockerImage;
    private final ContainerSize size;
    private final List<ExtraContainer> extraContainers;

    Configuration(boolean enabled, String dockerImage, ContainerSize size, List<ExtraContainer> extraContainers) {
        this.enabled = enabled;
        this.dockerImage = dockerImage;
        this.size = size;
        this.extraContainers = extraContainers;
    }

    public void overrideDockerImage() {
        this.dockerImage = overrideRegistry(dockerImage, getRegistryOverrides());
    }

    public boolean isEnabled() {
        return enabled;
    }

    @VisibleForTesting
    static String overrideRegistry(String imageString, Map<String, String> registryMapping) {
        String[] parts = imageString.split("/", 2);
        if (parts.length == 2 && (parts[0].contains(".") || parts[0].contains(":"))) {
            String registry = parts[0];
            String rest = parts[1];
            if (registryMapping.containsKey(registry)) {
                return registryMapping.get(registry) + "/" + rest;
            }
        }
        return imageString;
    }

    private Map<String, String> getRegistryOverrides() {
        String stringMap = System.getProperty(PROPERTY_DOCKER_REGISTRY_MAPPING);
        if (stringMap == null) {
            return new HashMap<>();
        } else {
            return registryOverrideStringToMap(stringMap);
        }
    }

    @VisibleForTesting
    static Map<String, String> registryOverrideStringToMap(String stringMap) {
        List<String> list = Arrays.asList(stringMap.split(","));
        // don't throw an exception if list is malformed.
        if (list.size() % 2 != 0) {
            return new HashMap<>();
        }

        Iterator<String> it = list.iterator();
        Map<String, String> registryMap = new HashMap<>();
        while (it.hasNext()) {
            registryMap.put(it.next(), it.next());
        }
        return registryMap;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public ContainerSize getSize() {
        return size;
    }
    
    public int getCPUTotal() {
        return size.cpu() + extraContainers.stream().mapToInt((ExtraContainer value) -> value.getExtraSize().cpu).sum();
    }
    
    public int getMemoryTotal() {
        return size.memory() + DOCKER_MINIMUM_MEMORY
                + extraContainers.stream().mapToInt((ExtraContainer value) -> value.getExtraSize().memory).sum();
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
        storageMap.put(Configuration.DOCKER_EXTRA_CONTAINERS,
                ConfigurationPersistence.toJson(getExtraContainers()).toString());
    }
    
    public static void removeFrom(Map<String, ? super String> storageMap) {
        storageMap.remove(Configuration.ENABLED_FOR_JOB);
        storageMap.remove(Configuration.DOCKER_IMAGE);
        storageMap.remove(Configuration.DOCKER_IMAGE_SIZE);
        storageMap.remove(Configuration.DOCKER_EXTRA_CONTAINERS);
    }

    
    public static enum ContainerSize {
        XLARGE(4096, 16000),
        LARGE(3072, 12000),
        REGULAR(2048, 8000),
        SMALL(1024, 4000);

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
        private final String name;
        private final String image;
        private ExtraContainerSize extraSize = ExtraContainerSize.REGULAR;
        private List<String> commands = Collections.emptyList();
        private List<EnvVariable> envVariables = Collections.emptyList();

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

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(@Nonnull List<String> commands) {
            this.commands = Collections.unmodifiableList(commands);
        }

        public List<EnvVariable> getEnvVariables() {
            return envVariables;
        }

        public void setEnvVariables(@Nonnull List<EnvVariable> envVariables) {
            this.envVariables = Collections.unmodifiableList(envVariables);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.name);
            hash = 59 * hash + Objects.hashCode(this.image);
            hash = 59 * hash + Objects.hashCode(this.extraSize);
            hash = 59 * hash + Objects.hashCode(this.commands);
            hash = 59 * hash + Objects.hashCode(this.envVariables);
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
            if (this.extraSize != other.extraSize) {
                return false;
            }
            if (!Objects.equals(this.commands, other.commands)) {
                return false;
            }
            return Objects.equals(this.envVariables, other.envVariables);
        }
    }
    
    public static enum ExtraContainerSize {
        XLARGE(2048, 8000),
        LARGE(1024, 4000),
        REGULAR(512, 2000),
        SMALL(256, 1000);
        
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

    public static final class EnvVariable {

        private final String name;
        private final String value;

        public EnvVariable(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 41 * hash + Objects.hashCode(this.name);
            hash = 41 * hash + Objects.hashCode(this.value);
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
            final EnvVariable other = (EnvVariable) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return Objects.equals(this.value, other.value);
        }
        
    }
}
