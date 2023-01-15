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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang.StringUtils;

public final class ConfigurationBuilder {

    public static ConfigurationBuilder create(String dockerImage) {
        return new ConfigurationBuilder(dockerImage);
    }

    private final String dockerImage;
    private String awsRole;
    private Configuration.ContainerSize size = Configuration.ContainerSize.REGULAR;
    private boolean enabled = true;
    private final List<Configuration.ExtraContainer> extras = new ArrayList<>();
    private String architecture;
    private final HashSet<String> featureFlags = new HashSet<>();

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

    public ConfigurationBuilder withExtraContainer(String name, String image, Configuration.ExtraContainerSize size) {
        this.extras.add(new Configuration.ExtraContainer(name, image, size));
        return this;
    }

    public ConfigurationBuilder withExtraContainer(Configuration.ExtraContainer ex) {
        this.extras.add(ex);
        return this;
    }

    public ConfigurationBuilder withExtraContainers(List<Configuration.ExtraContainer> extras) {
        this.extras.addAll(extras);
        return this;
    }

    public ConfigurationBuilder withAwsRole(String awsRole) {
        this.awsRole = awsRole;
        return this;
    }

    public ConfigurationBuilder withArchitecture(String architecture) {
        if (StringUtils.isNotBlank(architecture)) {
            this.architecture = architecture;
        }
        return this;
    }

    public ConfigurationBuilder withFeatureFlag(String featureFlag) {
        if (StringUtils.isNotBlank(featureFlag)) {
            this.featureFlags.add(featureFlag);
        }
        return this;
    }

    public ConfigurationBuilder withFeatureFlags(HashSet<String> featureFlags) {
        this.featureFlags.addAll(featureFlags);
        return this;
    }

    public Configuration build() {
        return new Configuration(enabled, dockerImage, awsRole, architecture, size, extras, featureFlags);
    }
}
