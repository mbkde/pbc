/*
 * Copyright 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.rest;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Simply used for REST JSON serialization/deserialization.
 */
public class Config {
    public Boolean enabled;
    public String defaultImage;
    public Integer maxAgentCreationPerMinute;
    public Integer agentCleanupTime;
    public Integer agentRemovalTime;
    public String architectureConfig;
    public boolean awsVendor;

    public Config() {}

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultImage() {
        return defaultImage;
    }

    public void setDefaultImage(String defaultImage) {
        this.defaultImage = defaultImage;
    }

    public Integer getMaxAgentCreationPerMinute() {
        return maxAgentCreationPerMinute;
    }

    public void setMaxAgentCreationPerMinute(Integer maxAgentCreationPerMinute) {
        this.maxAgentCreationPerMinute = maxAgentCreationPerMinute;
    }

    public Integer getAgentCleanupTime() {
        return agentCleanupTime;
    }

    public void setAgentCleanupTime(Integer agentCleanupTime) {
        this.agentCleanupTime = agentCleanupTime;
    }

    public Integer getAgentRemovalTime() {
        return agentRemovalTime;
    }

    public void setAgentRemovalTime(Integer agentRemovalTime) {
        this.agentRemovalTime = agentRemovalTime;
    }

    public String getArchitectureConfig() {
        return architectureConfig;
    }

    public void setArchitectureConfig(String architectureConfig) {
        this.architectureConfig = architectureConfig;
    }

    public boolean isAwsVendor() {
        return awsVendor;
    }

    public void setAwsVendor(boolean awsVendor) {
        this.awsVendor = awsVendor;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.NO_CLASS_NAME_STYLE);
    }
}
