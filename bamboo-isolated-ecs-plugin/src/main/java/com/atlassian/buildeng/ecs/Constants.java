package com.atlassian.buildeng.ecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.LogConfiguration;
import com.amazonaws.services.ecs.model.LogDriver;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;

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

    // fluentd config
    // LaaS requirements
    String LAAS_SERVICE_ID_KEY  = "serviceId";
    String LAAS_SERVICE_ID_VAL  = "ryzicKpx";
    String LAAS_ENVIRONMENT_KEY = "environment";
    String LAAS_ENVIRONMENT_VAL = "aws-us-east-1-ecs";
    // AWS info
    String ECS_CLUSTER_KEY                = "ecs_cluster";
    String ECS_CONTAINER_INSTANCE_ARN_KEY = "ecs-container-arn";
    String ENV_HOSTNAME = "HOSTNAME";

    
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
                            ECS_CONTAINER_INSTANCE_ARN_KEY,
                            ENV_VAR_RESULT_ID, 
                            ENV_HOSTNAME))
                    .addOptionsEntry("fluentd-address", "fluentd.staging.aws.buildeng.atlassian.com:24224");

    // The container definition of the sidekick
    ContainerDefinition SIDEKICK_DEFINITION =
            new ContainerDefinition()
                    .withName(SIDEKICK_CONTAINER_NAME)
                    .withCpu(Configuration.SIDEKICK_CPU)
                    .withMemory(Configuration.SIDEKICK_MEMORY)
                    .withEssential(false);

}