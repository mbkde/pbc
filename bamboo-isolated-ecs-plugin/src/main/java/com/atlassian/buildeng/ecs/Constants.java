package com.atlassian.buildeng.ecs;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.VolumeFrom;

/**
 * Created by obrent on 8/02/2016.
 */
public interface Constants {
    // Bandana access keys
    static final String BANDANA_CLUSTER_KEY = "com.atlassian.buildeng.ecs.cluster";
    static final String BANDANA_DOCKER_MAPPING_KEY = "com.atlassian.buildeng.ecs.docker";

    // ECS

    // The name of the sidekick docker image and sidekick container
    static final String SIDEKICK_NAME = "bamboo-agent-sidekick";

    // The name of the agent container
    static final String AGENT_NAME = "bamboo-agent";

    // The name used for the generated task definition (a.k.a. family)
    static final String TASK_DEFINITION_NAME = "staging-bamboo-generated";

    // The name of the atlassian docker registry
    // TODO: This currently uses the ECR registry, once vault/secrets are done revert to docker.atlassian.io
    static final String SIDEKICK_REPOSITORY = "960714566901.dkr.ecr.us-east-1.amazonaws.com/bamboo-agent-sidekick";

    // The default cluster to use
    static final String DEFAULT_CLUSTER = "staging_bamboo";

    // The environment variable to override on the agent per image
    static final String IMAGE_ENV_VAR = "IMAGE_ID";

    // The environment variable to overide on the agent per server
    static final String SERVER_ENV_VAR = "BAMBOO_SERVER";

    // The working directory of isolated agents
    static final String WORK_DIR = "/root/buildeng";

    // The script which runs the bamboo agent jar appropriately
    static final String RUN_SCRIPT = WORK_DIR + "/" + "run-agent.sh";

    // The running server url
    // TODO: Remove from Constants, and have configurable per serve
    static final String THIS_SERVER_URL = "https://staging-bamboo.internal.atlassian.com";

    // The container definition of the sidekick
    static final ContainerDefinition SIDEKICK_DEFINITION =
            new ContainerDefinition()
                    .withName(SIDEKICK_NAME)
                    .withImage(SIDEKICK_REPOSITORY)
                    .withCpu(10)
                    .withMemory(512)
                    .withEssential(false);

    // The container definition of the standard spec build agent, sans docker image name
    static final ContainerDefinition AGENT_BASE_DEFINITION =
            new ContainerDefinition()
                    .withName(AGENT_NAME)
                    .withCpu(900)
                    .withMemory(3072)
                    .withVolumesFrom(new VolumeFrom().withSourceContainer(SIDEKICK_NAME))
                    .withCommand(RUN_SCRIPT)
                    .withWorkingDirectory(WORK_DIR)
                    .withEnvironment(new KeyValuePair().withName(SERVER_ENV_VAR).withValue(THIS_SERVER_URL));
}