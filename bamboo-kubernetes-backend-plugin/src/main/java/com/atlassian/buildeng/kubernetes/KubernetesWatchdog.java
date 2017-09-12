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
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Background job checking the state of the cluster.
 */
public class KubernetesWatchdog extends WatchdogJob {
    private static final String RESULT_ERROR = "custom.isolated.docker.error";
    private static final Long MAX_QUEUE_TIME_MINUTES = 100L;

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

    private void executeImpl(Map<String, Object> jobDataMap) {
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


        KubernetesClient client = new DefaultKubernetesClient();
        List<Pod> pods = client.pods()
                .withLabel(PodCreator.LABEL_BAMBOO_SERVER, globalConfiguration.getBambooBaseUrlAskKubeLabel())
                .list().getItems();
        Map<String, String> terminationReasons = new HashMap<>();
        List<Pod> killed = new ArrayList<>();

        // delete pods which have had the bamboo-agent container terminated
        for (Pod pod : pods) {
            Map<String, ContainerStatus> currentContainers = pod.getStatus().getContainerStatuses().stream()
                    .collect(Collectors.toMap(ContainerStatus::getName, x -> x));
            ContainerStatus agentContainer = currentContainers.get(PodCreator.CONTAINER_NAME_BAMBOOAGENT);
            // if agentContainer == null then the pod is still initializing
            if (agentContainer != null && agentContainer.getState().getTerminated() != null) {
                logger.info("Killing pod {} with terminated agent container: {}",
                        KubernetesHelper.getName(pod), agentContainer.getState());
                Boolean deleted = client.resource(pod).delete();
                if (deleted) {
                    terminationReasons.put(KubernetesHelper.getName(pod), agentContainer.getState().toString());
                    killed.add(pod);
                }
            }
        }
        pods.removeAll(killed);

        Map<String, Pod> nameToPod = pods.stream().collect(Collectors.toMap(KubernetesHelper::getName, x -> x));
        // Kill queued jobs waiting on pods that no longer exist or which have been queued for too long
        DockerAgentBuildQueue.currentlyQueued(buildQueueManager).forEach((CommonContext context) -> {
            CurrentResult current = context.getCurrentResult();
            String podName = current.getCustomBuildData().get(KubernetesIsolatedDockerImpl.RESULT_PREFIX
                    + KubernetesIsolatedDockerImpl.NAME);
            if (podName != null) {
                Pod pod = nameToPod.get(podName);
                if (pod == null) {
                    logger.info("Stopping job {} because pod {} no longer exists", context.getResultKey(), podName);
                    errorUpdateHandler.recordError(
                            context.getEntityKey(), "Build was not queued due to pod deletion: " + podName);

                    String errorMessage = "build killed as pod was terminated";
                    String terminationReason = terminationReasons.get(podName);
                    if (terminationReason != null) {
                        errorMessage = errorMessage + " with bamboo-agent container state: " + terminationReason;
                    }
                    current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                    generateRemoteFailEvent(context, errorMessage, podName, isolatedAgentService, eventPublisher);

                    killBuild(deploymentExecutionService, deploymentResultService, logger, buildQueueManager,
                            context, current);
                } else {
                    Date creationTime = Date.from(Instant.parse(pod.getMetadata().getCreationTimestamp()));
                    if (Duration.ofMillis(
                            System.currentTimeMillis() - creationTime.getTime()).toMinutes() > MAX_QUEUE_TIME_MINUTES) {
                        errorUpdateHandler.recordError(
                                context.getEntityKey(), "Build was not queued after " + MAX_QUEUE_TIME_MINUTES
                                        + " minutes." + " podName: " + podName);
                        String errorMessage = "build terminated for queuing for too long";
                        current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                        generateRemoteFailEvent(context, errorMessage, podName, isolatedAgentService, eventPublisher);

                        killBuild(deploymentExecutionService, deploymentResultService, logger, buildQueueManager,
                                context, current);
                        client.resource(pod).delete();
                    }
                }
            }
        });
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
}
