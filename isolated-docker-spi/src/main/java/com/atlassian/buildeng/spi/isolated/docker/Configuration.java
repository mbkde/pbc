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

import java.util.Collections;
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
    public static final String PROPERTY_PREFIX = "custom.isolated.docker";
    public static final String ENABLED_FOR_JOB = PROPERTY_PREFIX + ".enabled"; 
    public static final String DOCKER_IMAGE = PROPERTY_PREFIX + ".image"; 
    public static final String DOCKER_IMAGE_SIZE = PROPERTY_PREFIX + ".imageSize"; 
    public static final String DOCKER_EXTRA_CONTAINERS = PROPERTY_PREFIX + ".extraContainers"; 
    //task related equivalents of DOCKER_IMAGE and ENABLED_FOR_DOCKER but plan templates
    // don't like dots in names.
    public static final String TASK_DOCKER_IMAGE = "dockerImage";
    public static final String TASK_DOCKER_IMAGE_SIZE = "dockerImageSize";
    public static final String TASK_DOCKER_EXTRA_CONTAINERS = "extraContainers";
    public static final String TASK_DOCKER_ENABLE = "enabled";

    public static int DOCKER_MINIMUM_MEMORY = 4;

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

    public boolean isEnabled() {
        return enabled;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public ContainerSize getSize() {
        return size;
    }
    
    public int getCPUTotal() {
        return size.cpu()
                + extraContainers.stream().mapToInt((ExtraContainer value) -> value.getExtraSize().cpu).sum();
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
        return Objects.hash(dockerImage, size, extraContainers);
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

    //TODO temporary, eventually will be configurable
    private static final double SOFT_TO_HARD_LIMIT_RATIO = 1.25;
    
    public static enum ContainerSize {
        XXLARGE(5120, 20000, (int) (20000 * SOFT_TO_HARD_LIMIT_RATIO)),
        XLARGE(4096, 16000, (int) (16000 * SOFT_TO_HARD_LIMIT_RATIO)),
        LARGE(3072, 12000, (int) (12000 * SOFT_TO_HARD_LIMIT_RATIO)),
        REGULAR(2048, 8000, (int) (8000 * SOFT_TO_HARD_LIMIT_RATIO)),
        SMALL(1024, 4000, (int) (4000 * SOFT_TO_HARD_LIMIT_RATIO)),
        XSMALL(512, 2000, (int) (2000 * SOFT_TO_HARD_LIMIT_RATIO));

        private final int cpu;
        private final int memory;
        private final int memoryLimit;

        private ContainerSize(int cpu, int memory, int memoryLimit) {
            this.cpu = cpu;
            this.memory = memory;
            this.memoryLimit = memoryLimit;
        }

        public int cpu() {
            return cpu;
        }

        /**
         * the reservation amount or the container.
         * @return amount in megabytes
         */
        public int memory() {
            return memory;
        }
        
        /**
         * the hard limit that the containers cannot cross.
         * @return amount in megabytes
         */
        public int memoryLimit() {
            return memoryLimit;
        }
    }

    public static class ExtraContainer {
        private final String name;
        private String image;
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

        public void setImage(String image) {
            this.image = image;
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
            return Objects.hash(name, image, extraSize, commands, envVariables);
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
        XXLARGE(3072, 12000,(int) (12000 * SOFT_TO_HARD_LIMIT_RATIO)),
        XLARGE(2048, 8000, (int) (8000 * SOFT_TO_HARD_LIMIT_RATIO)),
        LARGE(1024, 4000, (int) (4000 * SOFT_TO_HARD_LIMIT_RATIO)),
        REGULAR(512, 2000, (int) (2000 * SOFT_TO_HARD_LIMIT_RATIO)),
        SMALL(256, 1000, (int) (1000 * SOFT_TO_HARD_LIMIT_RATIO));
        
        private final int cpu;
        private final int memory;
        private final int memoryLimit;

        private ExtraContainerSize(int cpu, int memory, int memoryLimit) {
            this.cpu = cpu;
            this.memory = memory;
            this.memoryLimit = memoryLimit;
        }

        public int cpu() {
            return cpu;
        }
        
        /**
         * the reservation amount or the container.
         * @return amount in megabytes
         */
        public int memory() {
            return memory;
        }

        /**
         * the hard limit that the containers cannot cross.
         * @return amount in megabytes
         */
        public int memoryLimit() {
            return memoryLimit;
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
            return Objects.hash(name, value);
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
