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

package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.MountPoint;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;

public interface Constants {

    /**
     * name of ecs container instance attribute and bamboo server/pbc service system property
     * that denotes what storage driver to pass to the docker-in-docker daemon.
     * With DinD it's important to keep storage drivers in sync for inner and outer daemon.
     * ECS container instance attribute takes precedence if present and is generally recommended.
     */
    String STORAGE_DRIVER_PROPERTY = "pbc.dind.storage.driver";

    /**
     * The versions of Docker in Docker specified in this system property (as a comma separated list,
     * e.g., "docker:1.12-dind,docker:1.11-dind") will be overridden with a newer version that
     * matches the system property below.
     */
    String PROPERTY_DIND_OVERRIDE_IMAGES = "pbc.dind.override.versions";

    /**
     * This is the version of Docker in Docker that will override the versions specified above.
     */
    String PROPERTY_DIND_IMAGE = "pbc.dind.version";

    /**
     * the storage driver used with docker on the ec2 instances in ecs. For docker-in-docker to work
     * we need to use this one in place of the default vfs that is terribly slow.
     */
    String storage_driver = System.getProperty(STORAGE_DRIVER_PROPERTY, "overlay2");

    /**
     * System property to drain instances with disconnected agents rather than killing the outright.
     * Requires closer observation when set, some instances won't drain on their own ever.
     */
    String PROPERTY_DRAIN_DISCONNECTED = "pbc.instance.termination.policy.draining";

    // ECS

    // The name of the sidekick docker image and sidekick container
    String SIDEKICK_CONTAINER_NAME = "bamboo-agent-sidekick";

    // The name of the agent container
    String AGENT_CONTAINER_NAME = "bamboo-agent";

    // The name of the metadata container
    String METADATA_CONTAINER_NAME = "bamboo-agent-metadata";

    // The name of the build directory volume for dind
    String BUILD_DIR_VOLUME_NAME = "build-dir";

    String METADATA_IMAGE = "docker.atlassian.io/buildeng/ecs-docker-metadata";

    String DOCKER_SOCKET = "/var/run/docker.sock";

    String DOCKER_SOCKET_VOLUME_NAME = "docker_socket";

    /**
     * The environment variable to override on the agent per image.
     */
    String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server.
     */
    String ENV_VAR_SERVER = "BAMBOO_SERVER";
    
    /**
     * The environment variable to set the result spawning up the agent.
     */
    String ENV_VAR_RESULT_ID = "RESULT_ID";

    // The working directory of isolated agents
    String WORK_DIR = "/buildeng";

    // The working directory for builds
    String BUILD_DIR = WORK_DIR + "/bamboo-agent-home/xml-data/build-dir";

    // The script which runs the bamboo agent jar appropriately
    String RUN_SCRIPT = WORK_DIR + "/" + "run-agent.sh";

    // AWS info
    String ECS_CLUSTER_KEY                = "ecs_cluster";
    String ECS_CONTAINER_INSTANCE_ARN_KEY = "ecs_container_arn";

    String RESULT_PART_TASKARN = "TaskARN";
    String RESULT_PART_EC2_INSTANCEID = "EC2InstanceId";
    String RESULT_PART_ECS_CONTAINERARN = "ECSContainerARN";

    /**
     * environment variable name for extra containers that when defined will be used
     * to tweak the extra container's ulimits. The format is space separated list of key pairs.
     * name=soft[:hard] where hard is optional and both soft and hard are expected to be numbers
     */
    String ENV_VAR_PBC_ULIMIT_OVERRIDE = "PBC_ULIMIT_OVERRIDE";

    /**
     * a space separated list of container names that the extra container should link to.
     *  makes no effort to sanitize the values in terms of circular dependencies between
     * the extra containers. Can never point to the main container (that one links to extra containers already)
     * 
     */
    String ENV_VAR_PBC_EXTRA_LINKS = "PBC_EXTRA_LINKS";

    /**
     * the maximum interval in minutes in what the state instances in cluster will be checked and
     * eventually killed. It should be a bit smaller than MINUTES_BEFORE_BILLING_CYCLE to allow
     * graceful killing within current billing cycle.
     */
    int POLLING_INTERVAL = 8;
    /**
     * minutes before billing cycle is reached. it should be a bit bigger than POLLING_INTERVAL
     * to let the service kill unused instances.
     */
    int MINUTES_BEFORE_BILLING_CYCLE = 10;

    // The container definition of the sidekick
    ContainerDefinition SIDEKICK_DEFINITION =
            new ContainerDefinition()
                    .withName(SIDEKICK_CONTAINER_NAME)
                    .withMemoryReservation(Configuration.DOCKER_MINIMUM_MEMORY)
                    .withEssential(false);

    ContainerDefinition METADATA_DEFINITION =
            new ContainerDefinition()
                    .withName(METADATA_CONTAINER_NAME)
                    .withMemoryReservation(Configuration.DOCKER_MINIMUM_MEMORY)
                    .withImage(METADATA_IMAGE)
                    .withMountPoints(new MountPoint().withContainerPath(DOCKER_SOCKET).withSourceVolume(DOCKER_SOCKET_VOLUME_NAME),
                                     new MountPoint().withContainerPath(BUILD_DIR).withSourceVolume(BUILD_DIR_VOLUME_NAME))
                    .withEssential(false);
}