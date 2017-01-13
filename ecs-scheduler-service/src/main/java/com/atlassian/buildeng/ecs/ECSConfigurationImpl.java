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
package com.atlassian.buildeng.ecs;

import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;

public class ECSConfigurationImpl implements ECSConfiguration, TaskDefinitionRegistrations.Backend {
    static final String ECS_TASK_DEF = "ECS_TASK_DEF";
    static final String ECS_ASG = "ECS_ASG";
    static final String ECS_CLUSTER = "ECS_CLUSTER";
    static final String ECS_LOG_DRIVER = "ECS_LOGDRIVER";
    /**
     * value is comma separated list of env variables to read and use as log options
     */
    static final String ECS_LOG_OPTIONS = "ECS_LOGOPTIONS";
    
    private final String cluster;
    private final String asg;
    private final String taskDefinitionName;
    private Map<String, Integer> ecsTaskMapping = new HashMap<>();
    private Map<Configuration, Integer> configurationMapping = new HashMap<>();
    private final String logDriver;
    private final Map<String, String> logOptionsMap;

    @Inject
    public ECSConfigurationImpl(@Named(ECS_ASG) String asg,
                                @Named(ECS_CLUSTER) String cluster,
                                @Named(ECS_TASK_DEF) String taskDef,
                                @Named(ECS_LOG_DRIVER) String logDriver,
                                @Named(ECS_LOG_OPTIONS) String logOptionsList) {
        this.asg = asg;
        this.cluster = cluster;
        this.taskDefinitionName = taskDef;
        this.logDriver = logDriver;
        this.logOptionsMap = createLogOptionsMap(logOptionsList);
    }

    private Map<String, String> createLogOptionsMap(String logOptionsList) {
        if (StringUtils.isBlank(logOptionsList)) {
            return Collections.emptyMap();
        }
        Map<String, String> toRet = new HashMap<>();
        Splitter.on(",").split(logOptionsList).forEach((String t) -> {
            String val = System.getenv(t);
            if (val != null) {
                toRet.put(t, val);
            }
        });
        return toRet;
    }


    @Override
    public String getCurrentCluster() {
        return cluster;
    }

    @Override
    public String getCurrentASG() {
        return asg;
    }

    @Override
    public String getTaskDefinitionName() {
        return taskDefinitionName;
    }

    @Override
    public String getLoggingDriver() {
        return StringUtils.isBlank(logDriver) ? null : logDriver;
    }

    @Override
    public Map<String, String> getLoggingDriverOpts() {
        return logOptionsMap;
    }

    @Override
    public Map<String, String> getEnvVars() {
        return Collections.emptyMap();
    }

    @Override
    public Map<Configuration, Integer> getAllRegistrations() {
        return configurationMapping;
    }

    @Override
    public Map<String, Integer> getAllECSTaskRegistrations() {
        return ecsTaskMapping;
    }

    @Override
    public void persistDockerMappingsConfiguration(Map<Configuration, Integer> dockerMappings, Map<String, Integer> taskRequestMappings) {
        this.configurationMapping = dockerMappings;
        this.ecsTaskMapping = taskRequestMappings;
    }
}
