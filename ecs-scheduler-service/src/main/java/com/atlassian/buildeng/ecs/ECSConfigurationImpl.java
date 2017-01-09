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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;

public class ECSConfigurationImpl implements ECSConfiguration, TaskDefinitionRegistrations.Backend {
    static final String ECS_TASK_DEF = "ECS_TASK_DEF";
    static final String ECS_ASG = "ECS_ASG";
    static final String ECS_CLUSTER = "ECS_CLUSTER";
    
    private final String cluster;
    private final String asg;
    private final String taskDefinitionName;
    private Map<String, Integer> ecsTaskMapping = new HashMap<>();
    private Map<Configuration, Integer> configurationMapping = new HashMap<>();

    @Inject
    public ECSConfigurationImpl(@Named(ECS_ASG) String asg,
                                @Named(ECS_CLUSTER) String cluster,
                                @Named(ECS_TASK_DEF) String taskDef) {
        this.asg = asg;
        this.cluster = cluster;
        this.taskDefinitionName = taskDef;
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
        return null;
    }

    @Override
    public Map<String, String> getLoggingDriverOpts() {
        return Collections.emptyMap();
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
