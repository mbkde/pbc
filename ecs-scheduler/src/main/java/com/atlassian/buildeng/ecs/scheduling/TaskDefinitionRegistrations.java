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

package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.Request;
import com.amazonaws.protocol.json.JsonClientMetadata;
import com.amazonaws.protocol.json.SdkJsonProtocolFactory;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.HostVolumeProperties;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LogConfiguration;
import com.amazonaws.services.ecs.model.MountPoint;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.Volume;
import com.amazonaws.services.ecs.model.VolumeFrom;
import com.amazonaws.services.ecs.model.transform.RegisterTaskDefinitionRequestMarshaller;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDefinitionRegistrations {
    private static final Logger logger = LoggerFactory.getLogger(TaskDefinitionRegistrations.class);

    public static boolean isDockerInDockerImage(String image) {
        return image.startsWith("docker:") && image.endsWith("dind");
    }
    
    public interface Backend {
        Map<Configuration, Integer> getAllRegistrations();
        Map<String, Integer> getAllECSTaskRegistrations();
        void persistDockerMappingsConfiguration(Map<Configuration, Integer> dockerMappings, Map<String, Integer> taskRequestMappings);
    }

    private final Backend backend;
    private final ECSConfiguration ecsConfiguration;

    @Inject
    public TaskDefinitionRegistrations(Backend backend, ECSConfiguration ecsConfiguration) {
        this.backend = backend;
        this.ecsConfiguration = ecsConfiguration;
    }


    private static ContainerDefinition withGlobalEnvVars(ContainerDefinition def, ECSConfiguration configuration) {
        configuration.getEnvVars().forEach((String key, String val) -> {
            def.withEnvironment(new KeyValuePair().withName(key).withValue(val));
        });
        return def.withEnvironment(new KeyValuePair().withName(Constants.ECS_CLUSTER_KEY).withValue(configuration.getCurrentCluster()));
    }

    private static ContainerDefinition withLogDriver(ContainerDefinition def, ECSConfiguration configuration) {
        String driver = configuration.getLoggingDriver();
        if (driver != null) {
            LogConfiguration ld = new LogConfiguration().withLogDriver(driver).withOptions(configuration.getLoggingDriverOpts());
            return def.withLogConfiguration(ld);
        }
        return def;
    }

    // Constructs a standard build agent task definition request with sidekick and generated task definition family
    public static RegisterTaskDefinitionRequest taskDefinitionRequest(Configuration configuration, ECSConfiguration globalConfiguration, BambooServerEnvironment env) {
        ContainerDefinition main = withLogDriver(withGlobalEnvVars(
                new ContainerDefinition()
                        .withName(Constants.AGENT_CONTAINER_NAME)
                        .withCpu(configuration.getSize().cpu())
                        .withMemoryReservation(configuration.getSize().memory())
                        .withMemory((int) (configuration.getSize().memory() * Constants.SOFT_TO_HARD_LIMIT_RATIO))
                        .withImage(configuration.getDockerImage())
                        .withVolumesFrom(new VolumeFrom().withSourceContainer(Constants.SIDEKICK_CONTAINER_NAME))
                        .withEntryPoint(Constants.RUN_SCRIPT)
                        .withWorkingDirectory(Constants.WORK_DIR)
                        .withMountPoints(new MountPoint().withContainerPath(Constants.BUILD_DIR).withSourceVolume(Constants.BUILD_DIR_VOLUME_NAME))
                        .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_SERVER).withValue(env.getBambooBaseUrl()))
                        .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_IMAGE).withValue(configuration.getDockerImage())),
                globalConfiguration), globalConfiguration);
        RegisterTaskDefinitionRequest req = new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(main, Constants.SIDEKICK_DEFINITION.withImage(env.getCurrentSidekick()), Constants.METADATA_DEFINITION)
                .withFamily(globalConfiguration.getTaskDefinitionName())
                .withVolumes(new Volume().withName(Constants.BUILD_DIR_VOLUME_NAME),
                             new Volume().withName(Constants.DOCKER_SOCKET_VOLUME_NAME).withHost(new HostVolumeProperties().withSourcePath(Constants.DOCKER_SOCKET)));

        configuration.getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
            ContainerDefinition d = new ContainerDefinition()
                    .withName(t.getName())
                    .withImage(t.getImage())
                    .withCpu(t.getExtraSize().cpu())
                    .withMemoryReservation(t.getExtraSize().memory())
                    .withMemory((int) (t.getExtraSize().memory() * Constants.SOFT_TO_HARD_LIMIT_RATIO))
                    .withMountPoints(new MountPoint().withContainerPath(Constants.BUILD_DIR).withSourceVolume(Constants.BUILD_DIR_VOLUME_NAME))
                    .withEssential(false);
            if (isDockerInDockerImage(t.getImage())) {
                //https://hub.docker.com/_/docker/
                //TODO align storage driver with whatever we are using? (overlay)
                //default is vfs safest but slowest option.
                d.setPrivileged(Boolean.TRUE);
                main.withEnvironment(new KeyValuePair().withName("DOCKER_HOST").withValue("tcp://" + t.getName() + ":2375"));
            }
            req.withContainerDefinitions(d);
            main.withLinks(t.getName());
        });
        if (env.getECSTaskRoleARN() != null) {
            req.withTaskRoleArn(env.getECSTaskRoleARN());
        }
        return req;
    }

    private static String createRegisterTaskDefinitionString(Configuration configuration, ECSConfiguration globalConfiguration, BambooServerEnvironment env) {
        RegisterTaskDefinitionRequestMarshaller rtdm = new RegisterTaskDefinitionRequestMarshaller(new SdkJsonProtocolFactory(new JsonClientMetadata()));
        Request<RegisterTaskDefinitionRequest> rr = rtdm.marshall(taskDefinitionRequest(configuration, globalConfiguration, env));
        try {
            return Streams.asString(rr.getContent(), "UTF-8");
        } catch (IOException ex) {
            logger.error("No way to turn Registration Task to string", ex);
            return null;
        }
    }
  /**
     * Synchronously register a docker image to be used with isolated docker builds
     *
     * @param configuration The configuration to register
     * @return The internal identifier for the registered image.
     */
    public int registerDockerImage(Configuration configuration, BambooServerEnvironment env) throws ImageAlreadyRegisteredException, ECSException {
        Map<String, Integer> registrationMappings = backend.getAllECSTaskRegistrations();
        String newReg = createRegisterTaskDefinitionString(configuration, ecsConfiguration, env);
        if (registrationMappings.containsKey(newReg)) {
            throw new ImageAlreadyRegisteredException(configuration.getDockerImage());
        }

        Map<Configuration, Integer> dockerMappings = backend.getAllRegistrations();
        Integer revision = registerDockerImageECS(configuration, env);
        dockerMappings.put(configuration, revision);
        registrationMappings.put(newReg, revision);
        backend.persistDockerMappingsConfiguration(dockerMappings, registrationMappings);
        return revision;
    }
    
    private Integer registerDockerImageECS(Configuration configuration, BambooServerEnvironment env) throws ECSException {
        try {
            RegisterTaskDefinitionRequest req = TaskDefinitionRegistrations.taskDefinitionRequest(configuration, ecsConfiguration, env);
            RegisterTaskDefinitionResult result = createClient().registerTaskDefinition(req);
            return result.getTaskDefinition().getRevision();
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }
 /**
     * find task definition registration for given configuration
     * @param configuration conf to use
     * @return either the revision or -1 when not found
     */
    public int findTaskRegistrationVersion(Configuration configuration, BambooServerEnvironment env) {
        String reg = createRegisterTaskDefinitionString(configuration, ecsConfiguration, env);

        Integer val = backend.getAllECSTaskRegistrations().get(reg);
        return val != null ? val : -1;
    }

    @VisibleForTesting
    AmazonECS createClient() {
        return new AmazonECSClient();
    }
}
