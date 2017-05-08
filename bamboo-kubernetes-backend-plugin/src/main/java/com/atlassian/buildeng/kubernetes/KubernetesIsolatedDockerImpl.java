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
package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration.ExtraContainer;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author mkleint
 */
public class KubernetesIsolatedDockerImpl implements IsolatedAgentService, LifecycleAware {
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
    private final String PLUGIN_JOB_KEY = "KubernetesIsolatedDockerImpl";
    private static final long PLUGIN_JOB_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();
    static final String CONTAINER_NAME_BAMBOOAGENT = "bamboo-agent";
    
    //configuration
    static final String KUB_HOST = "https://192.168.99.100:8443";
    static final String NAMESPACE_BAMBOO = "bamboo";

    // The working directory of isolated agents
    String WORK_DIR = "/buildeng";
    // The working directory for builds
    String BUILD_DIR = WORK_DIR + "/bamboo-agent-home/xml-data/build-dir";

    
    private final AdministrationConfigurationAccessor admConfAccessor;
    private final PluginScheduler pluginScheduler;

    public KubernetesIsolatedDockerImpl(AdministrationConfigurationAccessor admConfAccessor, PluginScheduler pluginScheduler) {
        this.admConfAccessor = admConfAccessor;
        this.pluginScheduler = pluginScheduler;
    }

    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
        try (KubernetesClient client = createKubernetesClient()) {
            //TODO only create namespace when creation of pod fails?
            if (!client.namespaces().list().getItems().stream()
                    .filter((Namespace t) -> NAMESPACE_BAMBOO.equals(t.getMetadata().getName()))
                    .findFirst().isPresent()) {
                System.out.println("no bamboo namespace, creating");
                client.namespaces().createNew().withNewMetadata().withName(NAMESPACE_BAMBOO).endMetadata().done();
            }
            Pod pod = createPod(request);
            System.out.println("POD:" + pod.toString());
            pod = client.pods().create(pod);
            //TODO do we extract useful information from the podstatus here?
        }
    }

    static KubernetesClient createKubernetesClient() throws KubernetesClientException {
        Config config = new ConfigBuilder()
                .withMasterUrl(KUB_HOST)
                .build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        return client;
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    private Pod createPod(IsolatedDockerAgentRequest r) {
        Configuration c = r.getConfiguration();
        PodBuilder pb;
        pb = new PodBuilder()
            .withNewMetadata()
                .withNamespace(NAMESPACE_BAMBOO)
                .withName(r.getResultKey().toLowerCase(Locale.ENGLISH) + "-" + r.getUniqueIdentifier().toString())
                .addToLabels("resultId", r.getResultKey())
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
                    .addNewEnv().withName(ENV_VAR_SERVER).withValue(admConfAccessor.getAdministrationConfiguration().getBaseUrl()).endEnv()
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

    private List<Container> createExtraContainers(List<ExtraContainer> extraContainers) {
        return extraContainers.stream().map((ExtraContainer t) -> {
            return new ContainerBuilder()
                .withName(t.getName())
                .withImage(t.getImage())
                .withImagePullPolicy("Always")
                .addNewVolumeMount(BUILD_DIR, "workdir", false, "")
                .withCommand(t.getCommands())
                .withEnv(t.getEnvVariables().stream()
                        .map((Configuration.EnvVariable t1) -> new EnvVarBuilder().withName(t1.getName()).withValue(t1.getValue()).build())
                        .collect(Collectors.toList()))
                .build();
        }).collect(Collectors.toList());
    }

    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        pluginScheduler.scheduleJob(PLUGIN_JOB_KEY, KubernetesWatchdog.class, config, new Date(), PLUGIN_JOB_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(PLUGIN_JOB_KEY);
    }
    
}
