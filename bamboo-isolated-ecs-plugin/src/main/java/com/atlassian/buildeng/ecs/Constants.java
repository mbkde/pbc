package com.atlassian.buildeng.ecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.VolumeFrom;

/**
 * Created by obrent on 8/02/2016.
 */
public interface Constants {
    // Bandana access keys
    static final String BANDANA_CLUSTER_KEY = "com.atlassian.buildeng.ecs.cluster";
    static final String BANDANA_DOCKER_MAPPING_KEY = "com.atlassian.buildeng.ecs.docker";
    static final String BANDANA_SIDEKICK_KEY = "com.atlassian.buildeng.ecs.sidekick";

    // ECS

    // The name of the sidekick docker image and sidekick container
    static final String SIDEKICK_CONTAINER_NAME = "bamboo-agent-sidekick";

    // The name of the agent container
    static final String AGENT_CONTAINER_NAME = "bamboo-agent";

    // The name used for the generated task definition (a.k.a. family)
    static final String TASK_DEFINITION_NAME = "staging-bamboo-generated";

    // The name of the atlassian docker registry sidekick
    static final String DEFAULT_SIDEKICK_REPOSITORY = "docker.atlassian.io/buildeng/bamboo-agent-sidekick";

    // The default cluster to use
    static final String DEFAULT_CLUSTER = "staging_bamboo";

    /**
     * The environment variable to override on the agent per image
     */ 
    static final String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server
     */
    static final String ENV_VAR_SERVER = "BAMBOO_SERVER";
    
    /**
     * The environment variable to set the result spawning up the agent
     */ 
    static final String ENV_VAR_RESULT_ID = "RESULT_ID";

    // The working directory of isolated agents
    static final String WORK_DIR = "/buildeng";

    // The script which runs the bamboo agent jar appropriately
    static final String RUN_SCRIPT = WORK_DIR + "/" + "run-agent.sh";

    int SIDEKICK_CPU = 10;
    int SIDEKICK_MEMORY = 256;
    int AGENT_CPU = 2000;
    int AGENT_MEMORY = 7800;

    int TASK_CPU = SIDEKICK_CPU + AGENT_CPU;
    int TASK_MEMORY = SIDEKICK_MEMORY + AGENT_MEMORY;

    // The container definition of the sidekick
    static final ContainerDefinition SIDEKICK_DEFINITION =
            new ContainerDefinition()
                    .withName(SIDEKICK_CONTAINER_NAME)
                    .withCpu(SIDEKICK_CPU)
                    .withMemory(SIDEKICK_MEMORY)
                    .withEssential(false);

    // The container definition of the standard spec build agent, sans docker image name
    static final ContainerDefinition AGENT_BASE_DEFINITION =
            new ContainerDefinition()
                    .withName(AGENT_CONTAINER_NAME)
                    .withCpu(AGENT_CPU)
                    .withMemory(AGENT_MEMORY)
                    .withVolumesFrom(new VolumeFrom().withSourceContainer(SIDEKICK_CONTAINER_NAME))
                    .withEntryPoint(RUN_SCRIPT)
                    .withWorkingDirectory(WORK_DIR);
}