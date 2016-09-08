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
package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import io.fabric8.kubernetes.api.model.DownwardAPIVolumeSource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author mkleint
 */
public class KubernetesIsolatedDockerImpl implements IsolatedAgentService {
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
    
    private final AdministrationConfigurationAccessor admConfAccessor;

    public KubernetesIsolatedDockerImpl(AdministrationConfigurationAccessor admConfAccessor) {
        this.admConfAccessor = admConfAccessor;
    }

    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
        Config config = new ConfigBuilder()
                .withMasterUrl("https://192.168.99.100:8443")
                .build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        Pod pod = createPod(request);
        System.out.println("POD:" + pod.toString());
        client.pods().create(pod);
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    private Pod createPod(IsolatedDockerAgentRequest r) {
        Configuration c = r.getConfiguration();
        PodBuilder pb = new PodBuilder()
            .withNewMetadata()
                .withNamespace("bamboo")
                .withName(r.getResultKey() + "-" + r.getUniqueIdentifier().toString())
                .addToLabels("resultId", r.getResultKey())
            .endMetadata()
            .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                    .withName("sidekick")
                    //more or less same data as regular sidekick, just extending alpine to be able to 
                    // run shell scripts inside + /kubernetes.sh script to copy to shared directory
                    .withImage("docker.atlassian.io/buildeng/bamboo-agent-sidekick-k8s")
//                    .withImagePullPolicy("Never")
                    .addNewVolumeMount("/buildeng", "bamboo-agent-sidekick", false)
                    //copies data from /buildeng-data to /buildeng + the /buildeng/kubernetes flag file
                    .withCommand("sh", "-c", "/kubernetes.sh")
                .endContainer()
                .addNewContainer()
                    .withName("bamboo-agent")
                    .withImage(c.getDockerImage())
//                    .withImagePullPolicy("Never")
                    .withWorkingDir("/buildeng")
                // containers in pod don't have startup ordering and don't wait for each other. We need to do manually
                    .withCommand("sh", "-c", "while [ ! -f /buildeng/kubernetes ]; do sleep 1; done; /buildeng/run-agent.sh")
                    .addNewEnv().withName(ENV_VAR_IMAGE).withValue(c.getDockerImage()).endEnv()
                    .addNewEnv().withName(ENV_VAR_RESULT_ID).withValue(r.getResultKey()).endEnv()
                    .addNewEnv().withName(ENV_VAR_SERVER).withValue(admConfAccessor.getAdministrationConfiguration().getBaseUrl()).endEnv()
                    .addNewVolumeMount("/buildeng", "bamboo-agent-sidekick", false)
                .endContainer()
                .addNewVolume()
                    .withName("bamboo-agent-sidekick")
                    .withNewEmptyDir().endEmptyDir()
                .endVolume()
            .endSpec();
                
                        
        return pb.build();
    }
    
}
