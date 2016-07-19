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

/**
 *
 * @author mkleint
 */
public final class ConfigurationBuilder {
    
    public static ConfigurationBuilder create(String dockerImage) {
        return new ConfigurationBuilder(dockerImage);
    }
    
    private final String dockerImage;
    private Configuration.ContainerSize size = Configuration.ContainerSize.REGULAR;
    private boolean enabled = true;

    private ConfigurationBuilder(String dockerImage) {
        this.dockerImage = dockerImage;
    }
    
    public ConfigurationBuilder withImageSize(Configuration.ContainerSize size) {
        this.size = size;
        return this;
    }
    
    public ConfigurationBuilder withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
    
    public Configuration build() {
        return new Configuration(enabled, dockerImage, size);
    }
    
}
