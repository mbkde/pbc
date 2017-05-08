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
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * @author mkleint
 */
public class KubernetesWatchdog implements PluginJob {

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        try (KubernetesClient client = KubernetesIsolatedDockerImpl.createKubernetesClient()) {
            //TODO do we want to repeatedly query or 'watch' for changes?
            //client.pods().watch();
            
            List<Pod> pods = client.pods().inNamespace(PodCreator.NAMESPACE_BAMBOO).list().getItems();
            List<Pod> agentDied = pods.stream().filter(new Predicate<Pod>() {
                @Override
                public boolean test(Pod t) {
                    return t.getStatus().getContainerStatuses().stream()
                            .filter((ContainerStatus t1) -> PodCreator.CONTAINER_NAME_BAMBOOAGENT.equals(t1.getName()))
                            .filter((ContainerStatus t1) -> t1.getState().getTerminated() != null).findFirst().isPresent();
                }
            }).collect(Collectors.toList());
            if (!agentDied.isEmpty()) {
                System.out.println("agents died, remove pods");
                client.pods().delete(agentDied);
                //TODO if job still queued, remove from bamboo queue. + add error
            }
            
        }
    }
    
}
