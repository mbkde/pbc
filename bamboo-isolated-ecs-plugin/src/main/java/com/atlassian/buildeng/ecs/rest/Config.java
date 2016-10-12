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

package com.atlassian.buildeng.ecs.rest;

import java.util.Map;

public class Config {

    public String ecsClusterName;
    public String autoScalingGroupName;
    public String sidekickImage;
    public LogConfiguration logConfiguration;
    public Map<String, String> envs;

    public Config() {
    }
    
    public Config(String ecsClusterName, String autoScalingGroupName, String sidekickImage) {
        this.ecsClusterName = ecsClusterName;
        this.autoScalingGroupName = autoScalingGroupName;
        this.sidekickImage = sidekickImage;
    }

    public String getEcsClusterName() {
        return ecsClusterName;
    }

    public void setEcsClusterName(String ecsClusterName) {
        this.ecsClusterName = ecsClusterName;
    }

    public String getAutoScalingGroupName() {
        return autoScalingGroupName;
    }

    public void setAutoScalingGroupName(String autoScalingGroupName) {
        this.autoScalingGroupName = autoScalingGroupName;
    }

    public String getSidekickImage() {
        return sidekickImage;
    }

    public void setSidekickImage(String sidekickImage) {
        this.sidekickImage = sidekickImage;
    }

    public LogConfiguration getLogConfiguration() {
        return logConfiguration;
    }

    public void setLogConfiguration(LogConfiguration logConfiguration) {
        this.logConfiguration = logConfiguration;
    }

    public Map<String, String> getEnvs() {
        return envs;
    }

    public void setEnvs(Map<String, String> envs) {
        this.envs = envs;
    }
    

    public static class LogConfiguration {
        public String driver;
        public Map<String, String> options;

        public LogConfiguration() {
        }

        public LogConfiguration(String driver, Map<String, String> options) {
            this.driver = driver;
            this.options = options;
        }

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public Map<String, String> getOptions() {
            return options;
        }

        public void setOptions(Map<String, String> options) {
            this.options = options;
        }

        
    }
}
