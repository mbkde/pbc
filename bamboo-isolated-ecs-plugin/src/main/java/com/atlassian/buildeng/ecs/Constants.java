package com.atlassian.buildeng.ecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LogConfiguration;
import com.amazonaws.services.ecs.model.LogDriver;
import com.amazonaws.services.ecs.model.VolumeFrom;

public interface Constants {

    // ECS

    // The name of the sidekick docker image and sidekick container
    String SIDEKICK_CONTAINER_NAME = "bamboo-agent-sidekick";

    // The name of the agent container
    String AGENT_CONTAINER_NAME = "bamboo-agent";

    // The name used for the generated task definition (a.k.a. family)
    String TASK_DEFINITION_SUFFIX = "-generated";

    // The name of the atlassian docker registry sidekick
    String DEFAULT_SIDEKICK_REPOSITORY = "docker.atlassian.io/buildeng/bamboo-agent-sidekick";

    // The default cluster to use
    String DEFAULT_CLUSTER = "default";

    /**
     * The environment variable to override on the agent per image
     */
    String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server
     */
    String ENV_VAR_SERVER = "BAMBOO_SERVER";
    
    /**
     * The environment variable to set the result spawning up the agent
     */
    String ENV_VAR_RESULT_ID = "RESULT_ID";

    // The working directory of isolated agents
    String WORK_DIR = "/buildeng";

    // The script which runs the bamboo agent jar appropriately
    String RUN_SCRIPT = WORK_DIR + "/" + "run-agent.sh";

    int SIDEKICK_CPU = 40;
    int SIDEKICK_MEMORY = 240;
    int AGENT_CPU = 2000;
    int AGENT_MEMORY = 7800;

    int TASK_CPU = SIDEKICK_CPU + AGENT_CPU;
    int TASK_MEMORY = SIDEKICK_MEMORY + AGENT_MEMORY;

    //m4.10xlarge
    int INSTANCE_CPU = 40960;
    int INSTANCE_MEMORY = 161186;

    // fluentd config

    // LaaS requirements
    String LAAS_SERVICE_ID_KEY  = "serviceId";
    String LAAS_SERVICE_ID_VAL  = "ryzicKpx";
    String LAAS_ENVIRONMENT_KEY = "environment";
    String LAAS_ENVIRONMENT_VAL = "aws-us-east-1-ecs";
    // AWS info
    String ECS_CLUSTER_KEY                = "ecs_cluster";
    String ECS_CLUSTER_VAL                = DEFAULT_CLUSTER;
    String ECS_CONTAINER_INSTANCE_ARN_KEY = "ecs-container-arn";

    
    long   PLUGIN_JOB_INTERVAL_MILLIS  =  60000L; //Reap once every 60 seconds
    String PLUGIN_JOB_KEY = "ecs-watchdog";
    String RESULT_PART_TASKARN = "TaskARN";
    
    //these 2 copied from bamboo-isolated-docker-plugin to avoid dependency
    String RESULT_PREFIX = "result.isolated.docker.";
    String RESULT_ERROR = "custom.isolated.docker.error";
    
    
    LogConfiguration LAAS_CONFIGURATION =
            new LogConfiguration()
                    .withLogDriver(LogDriver.Fluentd)
                    // LaaS requirements
                    .addOptionsEntry("env", String.join(",",
                            LAAS_SERVICE_ID_KEY,
                            LAAS_ENVIRONMENT_KEY,
                            ECS_CLUSTER_KEY,
                            ECS_CONTAINER_INSTANCE_ARN_KEY))
                    .addOptionsEntry("fluentd-address", "fluentd.staging.aws.buildeng.atlassian.com:24224");

    // The container definition of the sidekick
    ContainerDefinition SIDEKICK_DEFINITION =
            new ContainerDefinition()
                    .withName(SIDEKICK_CONTAINER_NAME)
                    .withCpu(SIDEKICK_CPU)
                    .withMemory(SIDEKICK_MEMORY)
                    .withEssential(false);

    // The container definition of the standard spec build agent, sans docker image name
    ContainerDefinition AGENT_BASE_DEFINITION =
            new ContainerDefinition()
                    .withName(AGENT_CONTAINER_NAME)
                    .withCpu(AGENT_CPU)
                    .withMemory(AGENT_MEMORY)
                    .withVolumesFrom(new VolumeFrom().withSourceContainer(SIDEKICK_CONTAINER_NAME))
                    .withEntryPoint(RUN_SCRIPT)
                    .withWorkingDirectory(WORK_DIR)
                    .withLogConfiguration(LAAS_CONFIGURATION)
                    .withEnvironment(new KeyValuePair().withName(LAAS_SERVICE_ID_KEY).withValue(LAAS_SERVICE_ID_VAL))
                    .withEnvironment(new KeyValuePair().withName(LAAS_ENVIRONMENT_KEY).withValue(LAAS_ENVIRONMENT_VAL))
                    .withEnvironment(new KeyValuePair().withName(ECS_CLUSTER_KEY).withValue(ECS_CLUSTER_VAL));
}