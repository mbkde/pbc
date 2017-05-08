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
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import io.fabric8.kubernetes.api.model.Job;
import io.fabric8.kubernetes.api.model.JobBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
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

/**
 *
 * @author mkleint
 */
public class KubernetesIsolatedDockerImpl implements IsolatedAgentService, LifecycleAware {
    private final String PLUGIN_JOB_KEY = "KubernetesIsolatedDockerImpl";
    private static final long PLUGIN_JOB_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    private final GlobalConfiguration globalConfiguration;
    private final PluginScheduler pluginScheduler;

    public KubernetesIsolatedDockerImpl(GlobalConfiguration globalConfiguration, PluginScheduler pluginScheduler) {
        this.pluginScheduler = pluginScheduler;
        this.globalConfiguration = globalConfiguration;
    }

    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
        try (KubernetesClient client = createKubernetesClient(globalConfiguration)) {
            //TODO only create namespace when creation of pod fails?
            if (!client.namespaces().list().getItems().stream()
                    .filter((Namespace t) -> PodCreator.NAMESPACE_BAMBOO.equals(t.getMetadata().getName()))
                    .findFirst().isPresent()) {
                System.out.println("no bamboo namespace, creating");
                client.namespaces().createNew().withNewMetadata().withName(PodCreator.NAMESPACE_BAMBOO).endMetadata().done();
            }
            Pod pod = PodCreator.create(request, globalConfiguration.getBambooBaseUrl());
            JobBuilder jb = new JobBuilder()
                    .withNewMetadata()
                        .withNamespace(PodCreator.NAMESPACE_BAMBOO)
                        .withName(request.getResultKey().toLowerCase(Locale.ENGLISH) + "-" + request.getUniqueIdentifier().toString())
                    .endMetadata()
                    .withNewSpec()
                        .withCompletions(1)
                        .withNewTemplate().withNewSpecLike(pod.getSpec()).endSpec().endTemplate()
                    .endSpec();

            System.out.println("POD:" + pod.toString());
            Job job = client.extensions().jobs().inNamespace(PodCreator.NAMESPACE_BAMBOO).create(jb.build());
            //TODO do we extract useful information from the podstatus here?
        }
    }

    static KubernetesClient createKubernetesClient(GlobalConfiguration globalConfiguration) throws KubernetesClientException {
        Config config = new ConfigBuilder()
                .withMasterUrl(globalConfiguration.getKubernetesURL())
                .build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        return client;
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        config.put("globalConfiguration", globalConfiguration);
        pluginScheduler.scheduleJob(PLUGIN_JOB_KEY, KubernetesWatchdog.class, config, new Date(), PLUGIN_JOB_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(PLUGIN_JOB_KEY);
    }
    
}
