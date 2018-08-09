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

import com.amazonaws.Request;
import com.amazonaws.protocol.json.JsonClientMetadata;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.HostVolumeProperties;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LogConfiguration;
import com.amazonaws.services.ecs.model.MountPoint;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.Ulimit;
import com.amazonaws.services.ecs.model.UlimitName;
import com.amazonaws.services.ecs.model.Volume;
import com.amazonaws.services.ecs.model.VolumeFrom;
import com.amazonaws.services.ecs.model.transform.RegisterTaskDefinitionRequestProtocolMarshaller;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.HostFolderMapping;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDefinitionRegistrations {
    private static final Logger logger = LoggerFactory.getLogger(TaskDefinitionRegistrations.class);

    public static boolean isDockerInDockerImage(String image) {
        return image.contains("docker:") && image.endsWith("dind");
    }

    //TODO this should be refactored to no expose the internal structure at all.
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

    private static RegisterTaskDefinitionRequest withHostVolumes(ContainerDefinition agentContainerDef, RegisterTaskDefinitionRequest req, BambooServerEnvironment env) {
        env.getHostFolderMappings().forEach((HostFolderMapping t) -> {
            req.withVolumes(new Volume().withName(t.getVolumeName())
                    .withHost(new HostVolumeProperties().withSourcePath(t.getHostPath())));
            agentContainerDef.withMountPoints(new MountPoint().withSourceVolume(t.getVolumeName())
                    .withContainerPath(t.getContainerPath()));
        });
        return req;
    }

    // Constructs a standard build agent task definition request with sidekick and generated task definition family
    public static RegisterTaskDefinitionRequest taskDefinitionRequest(Configuration configuration, 
            ECSConfiguration globalConfiguration, BambooServerEnvironment env) {
        ContainerSizeDescriptor sizeDescriptor = globalConfiguration.getSizeDescriptor();
        ContainerDefinition main = withLogDriver(withGlobalEnvVars(
                new ContainerDefinition()
                        .withName(Constants.AGENT_CONTAINER_NAME)
                        .withCpu(sizeDescriptor.getCpu(configuration.getSize()))
                        .withMemoryReservation(sizeDescriptor.getMemory(configuration.getSize()))
                        .withMemory(sizeDescriptor.getMemoryLimit(configuration.getSize()))
                        .withImage(sanitizeImageName(configuration.getDockerImage()))
                        .withVolumesFrom(new VolumeFrom().withSourceContainer(Constants.SIDEKICK_CONTAINER_NAME))
                        .withEntryPoint(Constants.RUN_SCRIPT)
                        .withWorkingDirectory(Constants.WORK_DIR)
                        .withMountPoints(new MountPoint().withContainerPath(Constants.BUILD_DIR).withSourceVolume(Constants.BUILD_DIR_VOLUME_NAME))
                        .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_SERVER).withValue(env.getBambooBaseUrl()))
                        .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_IMAGE).withValue(configuration.getDockerImage())),
                globalConfiguration), globalConfiguration);
        RegisterTaskDefinitionRequest req =
                withHostVolumes(main,
                        new RegisterTaskDefinitionRequest()
                                .withContainerDefinitions(main, Constants.SIDEKICK_DEFINITION.withImage(env.getCurrentSidekick())) //, Constants.METADATA_DEFINITION)
                                .withFamily(globalConfiguration.getTaskDefinitionName())
                                .withVolumes(new Volume().withName(Constants.BUILD_DIR_VOLUME_NAME)),
                        env);

        configuration.getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
            ContainerDefinition d = withLogDriver(new ContainerDefinition()
                    .withName(sanitizeImageName(t.getName()))
                    .withImage(sanitizeImageName(t.getImage()))
                    .withCpu(sizeDescriptor.getCpu(t.getExtraSize()))
                    .withMemoryReservation(sizeDescriptor.getMemory(t.getExtraSize()))
                    .withMemory(sizeDescriptor.getMemoryLimit(t.getExtraSize()))
                    .withUlimits(generateUlimitList(t))
                    .withLinks(generateExtraContainerLinks(t))
                    .withMountPoints(new MountPoint().withContainerPath(Constants.BUILD_DIR).withSourceVolume(Constants.BUILD_DIR_VOLUME_NAME))
                    .withEssential(false), globalConfiguration);
            if (isDockerInDockerImage(t.getImage())) {
                //https://hub.docker.com/_/docker/
                String versions = System.getProperty(Constants.PROPERTY_DIND_OVERRIDE_IMAGES);
                if (versions != null) {
                    List<String> overrideVersions = Arrays.asList(versions.split(","));
                    String image = System.getProperty(Constants.PROPERTY_DIND_IMAGE);
                    if (overrideVersions.contains(t.getImage()) && image != null) {
                        d.setImage(image);
                    }
                }
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
        RegisterTaskDefinitionRequestProtocolMarshaller rtdm = new RegisterTaskDefinitionRequestProtocolMarshaller(new com.amazonaws.protocol.json.SdkJsonProtocolFactory(
            new JsonClientMetadata()
                    .withProtocolVersion("1.1")
                    .withSupportsCbor(false)
                    .withSupportsIon(false)));
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
    private static final Object registerLock = new Object();
    public int registerDockerImage(Configuration configuration, BambooServerEnvironment env) throws ECSException {
        synchronized (registerLock) {
            Map<String, Integer> registrationMappings = backend.getAllECSTaskRegistrations();
            String newReg = createRegisterTaskDefinitionString(configuration, ecsConfiguration, env);
            if (registrationMappings.containsKey(newReg)) {
                return registrationMappings.get(newReg);
            }

            Map<Configuration, Integer> dockerMappings = backend.getAllRegistrations();
            Integer revision = registerDockerImageECS(configuration, env);
            dockerMappings.put(configuration, revision);
            registrationMappings.put(newReg, revision);
            backend.persistDockerMappingsConfiguration(dockerMappings, registrationMappings);
            return revision;
        }
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

    private static Collection<String> generateExtraContainerLinks(Configuration.ExtraContainer t) {
        List<String> toRet = new ArrayList<>();
        String extraLinks = getExtraLinksEnvVar(t);
        if (extraLinks != null) {
            Splitter.on(" ").split(extraLinks).forEach((String t2) -> {
                toRet.add(t2);
            });
        }
        return toRet;
    }

    private static Collection<Ulimit> generateUlimitList(Configuration.ExtraContainer t) {
        List<Ulimit> toRet = new ArrayList<>();
        String ulimitsTweaks = getUlimitTweaksEnvVar(t);
        if (ulimitsTweaks != null) {
            Splitter.on(" ").split(ulimitsTweaks).forEach((String t2) -> {
                String[] oneLimit = t2.split("=");
                if (oneLimit.length == 2) {
                    //name=val
                    String name = oneLimit[0];
                    String soft;
                    String hard = null;
                    if (oneLimit[1].contains(":")) {
                        //name=soft:hard
                        String[] vals = oneLimit[1].split(":");
                        soft = vals[0];
                        hard = vals[1];
                    } else {
                        soft = oneLimit[1];
                    }
                    try {
                        Ulimit limit = new Ulimit();
                        limit.setName(UlimitName.fromValue(name));
                        limit.setSoftLimit(Integer.valueOf(soft));
                        if (hard != null) {
                            limit.setHardLimit(Integer.valueOf(hard));
                        }
                        toRet.add(limit);
                    } catch (NumberFormatException x) {
                        //wrong number
                        logger.error("PBC_ULIMIT_OVERRIDE contains wrongly formatted ulimit values: {}", t2);
                    } catch (IllegalArgumentException x) {
                        //wrong limit name
                        logger.error("PBC_ULIMIT_OVERRIDE contains wrongly ulimit names: {}", t2);
                    }
                }
            });
        }
        return toRet;
    }

    private static String getUlimitTweaksEnvVar(Configuration.ExtraContainer t) {
        return getEnvVarValue(t, Constants.ENV_VAR_PBC_ULIMIT_OVERRIDE);
    }

    private static String getEnvVarValue(Configuration.ExtraContainer t, String envVarName) {
        return t.getEnvVariables().stream()
                .filter((Configuration.EnvVariable t1) -> envVarName.equals(t1.getName()))
                .findFirst()
                .map((Configuration.EnvVariable t1) -> t1.getValue())
                .orElse(null);
    }

    private static String getExtraLinksEnvVar(Configuration.ExtraContainer t) {
        return getEnvVarValue(t, Constants.ENV_VAR_PBC_EXTRA_LINKS);
    }

    public static String sanitizeImageName(String image) {
        return image.trim();
    }

}
