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

import com.atlassian.sal.api.scheduling.PluginJob;
import static com.google.common.base.Preconditions.checkNotNull;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author mkleint
 */
public class KubernetesWatchdog implements PluginJob {

    static KubernetesClient createKubernetesClient(GlobalConfiguration globalConfiguration) throws KubernetesClientException {
        Config config = new ConfigBuilder()
                .withMasterUrl(globalConfiguration.getKubernetesURL())
                .build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        return client;
    }
    @Override
    public void execute(Map<String, Object> jobDataMap) {
        GlobalConfiguration globalConfiguration = getService(GlobalConfiguration.class, "globalConfiguration", jobDataMap);
        try (KubernetesClient client = createKubernetesClient(globalConfiguration)) {
            //TODO do we want to repeatedly query or 'watch' for changes?
            //client.pods().watch();
            System.out.println("watchdog");
            List<Pod> pods = client.pods().inNamespace(globalConfiguration.getKubernetesNamespace()).list().getItems();
            List<Pod> agentDied = pods.stream().filter(new Predicate<Pod>() {
                @Override
                public boolean test(Pod t) {
                    return t.getStatus().getContainerStatuses().stream()
                            .filter((ContainerStatus t1) -> PodCreator.CONTAINER_NAME_BAMBOOAGENT.equals(t1.getName()))
                            .filter((ContainerStatus t1) -> t1.getState().getTerminated() != null).findFirst().isPresent();
                }
            }).collect(Collectors.toList());
            if (!agentDied.isEmpty()) {
                System.out.println("agents died, remove pods:" + Serialization.asYaml(agentDied));
//                for (Pod dead : agentDied) {
//                    LogWatch watch = client.pods().inNamespace(globalConfiguration.getKubernetesNamespace()).withName(dead.getMetadata().getName()).tailingLines(100).watchLog(System.out);
//                }
//                try {
//                    Thread.sleep(20000);
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(KubernetesWatchdog.class.getName()).log(Level.SEVERE, null, ex);
//                }
                client.pods().delete(agentDied);
//                TODO if job still queued, remove from bamboo queue. + add error
            }
            
        }
    }
    protected final <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
        final Object obj = checkNotNull(jobDataMap.get(serviceKey), "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }
    
}
