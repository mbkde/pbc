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

import com.atlassian.bamboo.v2.build.CurrentResult;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;

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
    public static final String DOCKER_AWS_ROLE = PROPERTY_PREFIX + ".awsRole";
    public static final String DOCKER_ARCHITECTURE = PROPERTY_PREFIX + ".architecture";

    //task related equivalents of DOCKER_IMAGE and ENABLED_FOR_DOCKER but plan templates
    // don't like dots in names.
    public static final String TASK_DOCKER_IMAGE = "dockerImage";
    public static final String TASK_DOCKER_ARCHITECTURE = "dockerArchitecture";
    public static final String TASK_DOCKER_IMAGE_SIZE = "dockerImageSize";
    public static final String TASK_DOCKER_AWS_ROLE = "awsRole";
    public static final String TASK_DOCKER_EXTRA_CONTAINERS = "extraContainers";
    public static final String TASK_DOCKER_ENABLE = "enabled";

    /**
     * properties with this prefix are stored in build result custom data 
     * detailing the sizes of main and extra containers.
     * The available suffixes are .[container_name].memory and .[container_name].memoryLimit
     * The main container's name is 'bamboo-agent', for extra containers the name equals the one configured by user.
     */
    public static final String DOCKER_IMAGE_DETAIL = PROPERTY_PREFIX + ".imageDetail"; 

    public static int DOCKER_MINIMUM_MEMORY = 4;

    //when storing using bandana/xstream transient means it's not to be serialized
    private final transient boolean enabled;
    private String dockerImage;
    private String awsRole;
    private String architecture;
    private final ContainerSize size;
    private final List<ExtraContainer> extraContainers;

    Configuration(boolean enabled, String dockerImage, String awsRole,
                  String architecture, ContainerSize size, List<ExtraContainer> extraContainers) {
        this.enabled = enabled;
        this.dockerImage = dockerImage;
        this.awsRole = awsRole;
        this.architecture = architecture;
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

    public String getAwsRole() {
        return awsRole;
    }

    public Boolean isAwsRoleDefined() {
        return !StringUtils.isEmpty(awsRole);
    }

    public String getArchitecture() {
        return architecture;
    }

    public Boolean isArchitectureDefined() {
        return !StringUtils.isBlank(architecture);
    }

    /**
     * calculate cpu requirements for entire configuration.
     * @param sizeDescriptor component able to resolve the symbolic size to numbers
     */
    public int getCPUTotal(ContainerSizeDescriptor sizeDescriptor) {
        return sizeDescriptor.getCpu(size)
                + extraContainers.stream()
                        .mapToInt((ExtraContainer value) -> sizeDescriptor.getCpu(value.getExtraSize())).sum();
    }
    
    /**
     * calculate memory requirements for entire configuration.
     * @param sizeDescriptor component able to resolve the symbolic size to numbers
     */
    public int getMemoryTotal(ContainerSizeDescriptor sizeDescriptor) {
        return sizeDescriptor.getMemory(size) + DOCKER_MINIMUM_MEMORY
                + extraContainers.stream()
                        .mapToInt((ExtraContainer value) -> sizeDescriptor.getMemory(value.getExtraSize())).sum();
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

    /**
     * Copy configuration snapshot to result for use by agent side extensions.
     */
    public void copyToResult(CurrentResult result, ContainerSizeDescriptor sizeDescriptor) {
        Map<String, String> storageMap = result.getCustomBuildData();
        storageMap.put(Configuration.ENABLED_FOR_JOB, "" + isEnabled());
        storageMap.put(Configuration.DOCKER_IMAGE, getDockerImage());
        storageMap.put(Configuration.DOCKER_IMAGE_SIZE, getSize().name());
        if (getArchitecture() != null) {
            storageMap.put(Configuration.DOCKER_ARCHITECTURE, getArchitecture());
        }
        if (getAwsRole() != null) {
            storageMap.put(Configuration.DOCKER_AWS_ROLE, getAwsRole());
        }
        storageMap.put(Configuration.DOCKER_EXTRA_CONTAINERS,
                ConfigurationPersistence.toJson(getExtraContainers()).toString());
        //write down memory limits into result, as agent components don't have access to ContainerSizeDescriptor
        storageMap.put(Configuration.DOCKER_IMAGE_DETAIL + ".bamboo-agent.memory", 
                "" + sizeDescriptor.getMemory(getSize()));
        storageMap.put(Configuration.DOCKER_IMAGE_DETAIL + ".bamboo-agent.memoryLimit", 
                "" + sizeDescriptor.getMemoryLimit(getSize()));
        storageMap.put(Configuration.DOCKER_IMAGE_DETAIL + ".bamboo-agent.cpu",
                "" + sizeDescriptor.getCpu(getSize()));
        getExtraContainers().forEach((ExtraContainer t) -> {
            storageMap.put(Configuration.DOCKER_IMAGE_DETAIL + "." + t.getName() + ".memory",
                    "" + sizeDescriptor.getMemory(t.getExtraSize()));
            storageMap.put(Configuration.DOCKER_IMAGE_DETAIL + "." + t.getName() + ".memoryLimit",
                    "" + sizeDescriptor.getMemoryLimit(t.getExtraSize()));
            storageMap.put(Configuration.DOCKER_IMAGE_DETAIL + "." + t.getName() + ".cpu",
                    "" + sizeDescriptor.getCpu(t.getExtraSize()));
        });
    }
    
    /**
     * Clear configuration from result data structures.
     */
    public static void removeFromResult(CurrentResult result, ContainerSizeDescriptor sizeDescriptor) {
        Map<String, String> storageMap = result.getCustomBuildData();
        storageMap.remove(Configuration.ENABLED_FOR_JOB);
        storageMap.remove(Configuration.DOCKER_IMAGE);
        storageMap.remove(Configuration.DOCKER_ARCHITECTURE);
        storageMap.remove(Configuration.DOCKER_IMAGE_SIZE);
        storageMap.remove(Configuration.DOCKER_AWS_ROLE);
        storageMap.remove(Configuration.DOCKER_EXTRA_CONTAINERS);
        storageMap.entrySet().removeIf(ent -> ent.getKey().startsWith(Configuration.DOCKER_IMAGE_DETAIL));
    }

    
    public enum ContainerSize {
        LARGE_8X,
        LARGE_4X,
        XXLARGE,
        XLARGE,
        LARGE,
        REGULAR,
        SMALL,
        XSMALL
    }

    public static class ExtraContainer {
        private final String name;
        private String image;
        private ExtraContainerSize extraSize;
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
    
    public enum ExtraContainerSize {
        LARGE_8X,
        LARGE_4X,
        XXLARGE,
        XLARGE,
        LARGE,
        REGULAR,
        SMALL
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
