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

import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import com.amazonaws.Request;
import com.amazonaws.protocol.json.JsonClientMetadata;
import com.amazonaws.protocol.json.SdkJsonProtocolFactory;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LogConfiguration;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.VolumeFrom;
import com.amazonaws.services.ecs.model.transform.RegisterTaskDefinitionRequestMarshaller;
import com.atlassian.buildeng.ecs.Constants;
import com.atlassian.buildeng.ecs.GlobalConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.io.IOException;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDefinitionRegistrations {
    private static final Logger logger = LoggerFactory.getLogger(TaskDefinitionRegistrations.class);

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
    public static RegisterTaskDefinitionRequest taskDefinitionRequest(Configuration configuration, ECSConfiguration globalConfiguration) {
        ContainerDefinition main = withLogDriver(withGlobalEnvVars(
                new ContainerDefinition()
                        .withName(Constants.AGENT_CONTAINER_NAME)
                        .withCpu(configuration.getSize().cpu())
                        .withMemoryReservation(configuration.getSize().memory())
                        .withImage(configuration.getDockerImage())
                        .withVolumesFrom(new VolumeFrom().withSourceContainer(Constants.SIDEKICK_CONTAINER_NAME))
                        .withEntryPoint(Constants.RUN_SCRIPT)
                        .withWorkingDirectory(Constants.WORK_DIR)
                        .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_SERVER).withValue(globalConfiguration.getBambooBaseUrl()))
                        .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_IMAGE).withValue(configuration.getDockerImage())),
                globalConfiguration), globalConfiguration);
        RegisterTaskDefinitionRequest req = new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(main, Constants.SIDEKICK_DEFINITION.withImage(globalConfiguration.getCurrentSidekick()))
                .withFamily(globalConfiguration.getTaskDefinitionName());
        configuration.getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
            ContainerDefinition d = new ContainerDefinition()
                    .withName(t.getName())
                    .withImage(t.getImage())
                    .withCpu(t.getExtraSize().cpu())
                    .withMemoryReservation(t.getExtraSize().memory())
                    .withEssential(false);
            if (GlobalConfiguration.isDockerInDockerImage(t.getImage())) {
                //https://hub.docker.com/_/docker/
                //TODO align storage driver with whatever we are using? (overlay)
                //default is vfs safest but slowest option.
                d.setPrivileged(Boolean.TRUE);
                main.withEnvironment(new KeyValuePair().withName("DOCKER_HOST").withValue("tcp://" + t.getName() + ":2375"));
            }
            req.withContainerDefinitions(d);
            main.withLinks(t.getName());
        });
        return req;
    }

    public static String createRegisterTaskDefinitionString(Configuration configuration, ECSConfiguration globalConfiguration) {
        RegisterTaskDefinitionRequestMarshaller rtdm = new RegisterTaskDefinitionRequestMarshaller(new SdkJsonProtocolFactory(new JsonClientMetadata()));
        Request<RegisterTaskDefinitionRequest> rr = rtdm.marshall(taskDefinitionRequest(configuration, globalConfiguration));
        try {
            return Streams.asString(rr.getContent(), "UTF-8");
        } catch (IOException ex) {
            logger.error("No way to turn Registration Task to string", ex);
            return null;
        }
    }

}