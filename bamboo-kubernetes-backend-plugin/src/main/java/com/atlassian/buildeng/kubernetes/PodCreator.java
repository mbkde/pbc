/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PodCreator {
    /**
     * The environment variable to override on the agent per image
     */
    static String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server
     */
    static String ENV_VAR_SERVER = "BAMBOO_SERVER";

    /**
     * The environment variable to set the result spawning up the agent
     */
    static String ENV_VAR_RESULT_ID = "RESULT_ID";

    // The working directory of isolated agents
    static final String WORK_DIR = "/buildeng";
    // The working directory for builds
    static final String BUILD_DIR = WORK_DIR + "/bamboo-agent-home/xml-data/build-dir";

    // Ratio between soft and hard limits
    static final Double SOFT_TO_HARD_LIMIT_RATIO = 1.25;
    
    static final String CONTAINER_NAME_BAMBOOAGENT = "bamboo-agent";
    static final String NAMESPACE_BAMBOO = "bamboo";


    static Pod create(IsolatedDockerAgentRequest r, String bambooUrl) {
        Configuration c = r.getConfiguration();
        PodBuilder pb = new PodBuilder()
            .withNewMetadata()
                .withNamespace(NAMESPACE_BAMBOO)
                .withName(r.getResultKey().toLowerCase(Locale.ENGLISH) + "-" + r.getUniqueIdentifier().toString())
                .addToLabels("resultId", r.getResultKey())
                .addToLabels("uuid", r.getUniqueIdentifier().toString())
            .endMetadata()
            .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                    .withName("bamboo-agent-sidekick")
                    //more or less same data as regular sidekick, just extending alpine to be able to
                    // run shell scripts inside + /kubernetes.sh script to copy to shared directory
                    .withImage("docker.atlassian.io/buildeng/bamboo-agent-sidekick-k8s")
                    //kubernetes fails to pull from docker.a.io and this skips the container. ??
                    .withImagePullPolicy("Always")
                    .addNewVolumeMount("/buildeng-data", "bamboo-agent-sidekick", false, "")
                    //copies data from /buildeng-data to /buildeng + the /buildeng/kubernetes flag file
                    .withCommand("sh", "-c", "cp -r /buildeng/* /buildeng-data;touch /buildeng-data/kubernetes")
                .endContainer()
                .addNewContainer()
                    .withName(CONTAINER_NAME_BAMBOOAGENT)
                    .withImage(c.getDockerImage())
                    .withImagePullPolicy("Always")
                    .withWorkingDir(WORK_DIR)
                    // containers in pod don't have startup ordering and don't wait for each other. We need to do manually
                    .withCommand("sh", "-c", "while [ ! -f /buildeng/kubernetes ]; do sleep 1; done; /buildeng/run-agent.sh")
                    .addNewEnv().withName(ENV_VAR_IMAGE).withValue(c.getDockerImage()).endEnv()
                    .addNewEnv().withName(ENV_VAR_RESULT_ID).withValue(r.getResultKey()).endEnv()
                    .addNewEnv().withName(ENV_VAR_SERVER).withValue(bambooUrl).endEnv()
                    .addNewEnv().withName("QUEUE_TIMESTAMP").withValue("" + r.getQueueTimestamp()).endEnv()
                    .addNewEnv().withName("SUBMIT_TIMESTAMP").withValue("" + System.currentTimeMillis()).endEnv()
                    .addNewVolumeMount(WORK_DIR, "bamboo-agent-sidekick", false, "")
                    .addNewVolumeMount(BUILD_DIR, "workdir", false, "")
                .endContainer()
                .withContainers(createExtraContainers(r.getConfiguration().getExtraContainers()))
                .addNewVolume()
                    .withName("bamboo-agent-sidekick")
                    .withNewEmptyDir().endEmptyDir()
                .endVolume()
                .addNewVolume()
                    .withName("workdir")
                    .withNewEmptyDir().endEmptyDir()
                .endVolume()
            .endSpec();


        return pb.build();
    }

    private static List<Container> createExtraContainers(List<Configuration.ExtraContainer> extraContainers) {
        return extraContainers.stream().map((Configuration.ExtraContainer t) -> {
            return new ContainerBuilder()
                .withName(t.getName())
                .withImage(t.getImage())
                .withImagePullPolicy("Always")
                .addNewVolumeMount(BUILD_DIR, "workdir", false, "")
                .withCommand(t.getCommands())
                .withEnv(t.getEnvVariables().stream()
                        .map((Configuration.EnvVariable t1) -> new EnvVarBuilder().withName(t1.getName()).withValue(t1.getValue()).build())
                        .collect(Collectors.toList()))
                .withNewResources()
                    .addToRequests("memory", new Quantity("" + t.getExtraSize().memory() + "Mi"))
                    .addToLimits("memory", new Quantity("" + t.getExtraSize().memory()  * SOFT_TO_HARD_LIMIT_RATIO + "Mi"))
                    .addToRequests("cpu", new Quantity("" + t.getExtraSize().cpu() + "m"))
//                        .addToLimits("cpu", new Quantity("" + c.getSize().cpu() * SOFT_TO_HARD_LIMIT_RATIO + "m"))
                .endResources()

                .build();
        }).collect(Collectors.toList());
    }

}
