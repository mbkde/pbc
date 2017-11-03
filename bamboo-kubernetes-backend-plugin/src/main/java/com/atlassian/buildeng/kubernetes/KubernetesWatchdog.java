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

import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentKubeFailEvent;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.WatchdogJob;
import com.atlassian.event.api.EventPublisher;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Background job checking the state of the cluster.
 */
public class KubernetesWatchdog extends WatchdogJob {
    private static final String RESULT_ERROR = "custom.isolated.docker.error";
    private static final String QUEUE_TIMESTAMP = "pbcJobQueueTime";
    private static final Long MAX_QUEUE_TIME_MINUTES = 100L;
    private static final String KEY_TERMINATED_POD_REASONS = "TERMINATED_PODS_MAP";
    private static final int MISSING_POD_GRACE_PERIOD_MINUTES = 1;
    private static final int MAX_BACKOFF_SECONDS = 100;

    private static final Logger logger = LoggerFactory.getLogger(KubernetesWatchdog.class);

    @Override
    public final void execute(Map<String, Object> jobDataMap) {
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) { 
            //this is throwable because of NoClassDefFoundError and alike. 
            // These are not Exception subclasses and actually
            // thowing something here will stop rescheduling the job forever (until next redeploy)
            logger.error("Exception caught and swallowed to preserve rescheduling of the task", t);
        }

    }

    private void executeImpl(Map<String, Object> jobDataMap)
            throws InterruptedException, IOException, KubernetesClient.KubectlException {
        BuildQueueManager buildQueueManager = getService(BuildQueueManager.class, "buildQueueManager");
        ErrorUpdateHandler errorUpdateHandler = getService(ErrorUpdateHandler.class, "errorUpdateHandler");
        EventPublisher eventPublisher = getService(EventPublisher.class, "eventPublisher");
        GlobalConfiguration globalConfiguration = getService(GlobalConfiguration.class,
                "globalConfiguration", jobDataMap);
        DeploymentExecutionService deploymentExecutionService = getService(
                DeploymentExecutionService.class, "deploymentExecutionService");
        DeploymentResultService deploymentResultService = getService(
                DeploymentResultService.class, "deploymentResultService");
        IsolatedAgentService isolatedAgentService = getService(
                IsolatedAgentService.class, "isolatedAgentService", jobDataMap);


        KubernetesClient client = new KubernetesClient(globalConfiguration);
        List<Pod> pods = client.getPods(
                PodCreator.LABEL_BAMBOO_SERVER, globalConfiguration.getBambooBaseUrlAskKubeLabel());
        Map<String, Pair<Date, String>> terminationReasons = getPodTerminationReasons(jobDataMap);
        List<Pod> killed = new ArrayList<>();
        
        // delete pods which have had the bamboo-agent container terminated
        Set<BackoffCache> newBackedOff = new HashSet<>();
        for (Pod pod : pods) {
            Map<String, ContainerStatus> currentContainers = pod.getStatus().getContainerStatuses().stream()
                    .collect(Collectors.toMap(ContainerStatus::getName, x -> x));
            ContainerStatus agentContainer = currentContainers.get(PodCreator.CONTAINER_NAME_BAMBOOAGENT);
            // if agentContainer == null then the pod is still initializing
            if (agentContainer != null && agentContainer.getState().getTerminated() != null) {
                logger.info("Killing pod {} with terminated agent container. Container states: {}",
                        KubernetesHelper.getName(pod),
                        pod.getStatus().getContainerStatuses().stream()
                                .map(t -> t.getState()).collect(Collectors.toList()));
                boolean deleted = deletePod(
                        client, pod, terminationReasons, agentContainer.getState().getTerminated().getReason());
                if (deleted) {
                    killed.add(pod);
                }
            }
            //identify if pod is stuck in "imagePullBackOff' loop.
            newBackedOff.addAll(pod.getStatus().getContainerStatuses().stream()
                    .filter((ContainerStatus t) -> t.getState().getWaiting() != null)
                    .filter((ContainerStatus t) -> 
                            "ImagePullBackOff".equals(t.getState().getWaiting().getReason())
                         || "ErrImagePull".equals(t.getState().getWaiting().getReason()))
                    .map((ContainerStatus t) -> 
                            new BackoffCache(KubernetesHelper.getName(pod), t.getName(), 
                                    t.getState().getWaiting().getMessage()))
                    .collect(Collectors.toList()));
        }
        Set<BackoffCache> backoffCache = getImagePullBackOffCache(jobDataMap);
        //retain only those pods that are still stuck in backoff
        backoffCache.retainAll(newBackedOff);
        backoffCache.addAll(newBackedOff);
        
        long currentTime = System.currentTimeMillis();
        backoffCache.stream()
                .filter((BackoffCache t) ->
                        MAX_BACKOFF_SECONDS < Duration.ofMillis(currentTime - t.creationTime.getTime()).getSeconds())
                .forEach((BackoffCache t) -> {
                    Pod pod = pods.stream()
                            .filter((Pod pod1) -> KubernetesHelper.getName(pod1).equals(t.podName))
                            .findFirst().orElse(null);
                    if (pod != null) {
                        logger.warn("Killing pod {} with container in ImagePullBackOff state: {}",
                                t.podName, t.message);
                        boolean deleted = deletePod(
                                client, pod, terminationReasons,
                            "Container '" + t.containerName + "' image pull failed");
                        if (deleted) {
                            killed.add(pod);
                        }
                    } else {
                        logger.warn("Could not find pod {} in the current list.", t.podName);
                    }
                });
        
        
        pods.removeAll(killed);

        Map<String, Pod> nameToPod = pods.stream().collect(Collectors.toMap(KubernetesHelper::getName, x -> x));
        // Kill queued jobs waiting on pods that no longer exist or which have been queued for too long
        DockerAgentBuildQueue.currentlyQueued(buildQueueManager).forEach((CommonContext context) -> {
            CurrentResult current = context.getCurrentResult();
            String podName = current.getCustomBuildData().get(KubernetesIsolatedDockerImpl.RESULT_PREFIX
                    + KubernetesIsolatedDockerImpl.NAME);
            if (podName != null) {
                Long queueTime = Long.parseLong(current.getCustomBuildData().get(QUEUE_TIMESTAMP));
                Pod pod = nameToPod.get(podName);

                if (pod == null) {
                    if (terminationReasons.get(podName) != null
                            || Duration.ofMillis(System.currentTimeMillis() - queueTime).toMinutes()
                                    >= MISSING_POD_GRACE_PERIOD_MINUTES) {
                        logger.info("Stopping job {} because pod {} no longer exists", context.getResultKey(), podName);
                        errorUpdateHandler.recordError(
                                context.getEntityKey(), "Build was not queued due to pod deletion: " + podName);

                        String errorMessage = "Build killed as pod was terminated";
                        Pair<Date, String> terminationReason = terminationReasons.get(podName);
                        if (terminationReason != null) {
                            errorMessage = errorMessage + " with reason: " + terminationReason.getRight();
                        }
                        current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                        generateRemoteFailEvent(context, errorMessage, podName, isolatedAgentService, eventPublisher);

                        killBuild(deploymentExecutionService, deploymentResultService, logger, buildQueueManager,
                                context, current);
                    } else {
                        logger.debug("Pod {} missing but still in grace period, not stopping the build.", podName);
                    }
                } else {
                    Date creationTime = Date.from(Instant.parse(pod.getMetadata().getCreationTimestamp()));
                    if (Duration.ofMillis(
                            System.currentTimeMillis() - creationTime.getTime()).toMinutes() > MAX_QUEUE_TIME_MINUTES) {
                        errorUpdateHandler.recordError(
                                context.getEntityKey(), "Build was not queued after " + MAX_QUEUE_TIME_MINUTES
                                        + " minutes." + " podName: " + podName);
                        String errorMessage = "Build terminated for queuing for too long";
                        current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                        generateRemoteFailEvent(context, errorMessage, podName, isolatedAgentService, eventPublisher);

                        killBuild(deploymentExecutionService, deploymentResultService, logger, buildQueueManager,
                                context, current);
                        client.deletePod(pod);
                    }
                }
            }
        });
    }
    
    
    Set<BackoffCache> getImagePullBackOffCache(Map<String, Object> jobDataMap) {
        Set<BackoffCache> list = (Set<BackoffCache>) jobDataMap.get("ImagePullBackOffCache");
        if (list == null) {
            list = new HashSet<>();
            jobDataMap.put("ImagePullBackOffCache", list);
        }
        return list;
    }

    private void generateRemoteFailEvent(CommonContext context, String error, String podName,
                                         IsolatedAgentService isolatedAgentService, EventPublisher eventPublisher) {
        Configuration config = AccessConfiguration.forContext(context);
        Map<String, String> customData = new HashMap<>(context.getCurrentResult().getCustomBuildData());
        customData.entrySet().removeIf(
            (Map.Entry<String, String> tt) -> !tt.getKey().startsWith(KubernetesIsolatedDockerImpl.RESULT_PREFIX));
        Map<String, URL> containerLogs = isolatedAgentService.getContainerLogs(config, customData);

        eventPublisher.publish(new DockerAgentKubeFailEvent(error, context.getEntityKey(), podName, containerLogs));
    }

    private boolean deletePod(
            KubernetesClient client, Pod pod,
            Map<String, Pair<Date, String>> terminationReasons, String terminationReason) {
        String describePod = "";
        try {
            describePod = client.describePod(pod);
        } catch (IOException | KubernetesClient.KubectlException | InterruptedException e) {
            logger.error("Could not describe pod {}", KubernetesHelper.getName(pod));
        }
        boolean deleted = client.deletePod(pod);
        if (deleted) {
            logger.info("Pod {} successfully deleted. Final state:\n{}", KubernetesHelper.getName(pod), describePod);
            terminationReasons.put(
                    KubernetesHelper.getName(pod),
                    new ImmutablePair<>(new Date(), terminationReason));
            return true;
        } else {
            logger.error("Could not delete pod {}", KubernetesHelper.getName(pod));
        }
        return false;
    }

    private Map<String, Pair<Date, String>> getPodTerminationReasons(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        Map<String, Pair<Date, String>> map = (Map<String, Pair<Date, String>>) data.get(KEY_TERMINATED_POD_REASONS);
        if (map == null) {
            map = new HashMap<>();
            data.put(KEY_TERMINATED_POD_REASONS, map);
        }
        // trim values that are too old to have
        map.entrySet().removeIf(next ->
                Duration.ofMillis(System.currentTimeMillis() - next.getValue().getLeft().getTime()).toMinutes()
                        >= MISSING_POD_GRACE_PERIOD_MINUTES);
        return map;
    }

    private static class BackoffCache {
        private final String podName;
        private final Date creationTime;
        private final String containerName;
        private final String message;
        
        BackoffCache(String podName, String containerName, String message) {
            this.podName = podName;
            this.creationTime = new Date();
            this.containerName = containerName;
            this.message = message;
        }

        //for hashcode and equals, only consider podName + container name, not date, for easier cache manipulation.

        @Override
        public int hashCode() {
            return Objects.hash(podName, containerName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BackoffCache other = (BackoffCache) obj;
            if (!Objects.equals(this.podName, other.podName)) {
                return false;
            }
            return Objects.equals(this.containerName, other.containerName);
        }
        
    }
}
