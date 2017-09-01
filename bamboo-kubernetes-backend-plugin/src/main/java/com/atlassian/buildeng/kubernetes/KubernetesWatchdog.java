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

import static com.google.common.base.Preconditions.checkNotNull;

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.security.ImpersonationHelper;
import com.atlassian.bamboo.utils.BambooRunnables;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

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
public class KubernetesWatchdog implements PluginJob {
    private static final String RESULT_ERROR = "custom.isolated.docker.error";
    private static final Long MAX_QUEUE_TIME_MINUTES = 100L;

    private static final Logger logger = LoggerFactory.getLogger(KubernetesWatchdog.class);

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        BuildQueueManager buildQueueManager = getService(BuildQueueManager.class, "buildQueueManager");
        ErrorUpdateHandler errorUpdateHandler = getService(ErrorUpdateHandler.class, "errorUpdateHandler");
        GlobalConfiguration globalConfiguration = getService(GlobalConfiguration.class,
                "globalConfiguration", jobDataMap);

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
                    killBuild(buildQueueManager, context, current);
                } else {
                    Date creationTime = Date.from(Instant.parse(pod.getMetadata().getCreationTimestamp()));
                    if (Duration.ofMillis(
                            System.currentTimeMillis() - creationTime.getTime()).toMinutes() > MAX_QUEUE_TIME_MINUTES) {
                        errorUpdateHandler.recordError(
                                context.getEntityKey(), "Build was not queued after " + MAX_QUEUE_TIME_MINUTES
                                        + "podName: " + podName);
                        current.getCustomBuildData().put(RESULT_ERROR, "build terminated for queuing for too long");
                        killBuild(buildQueueManager, context, current);
                        client.resource(pod).delete();
                    }
                }
            }
        });
    }

    private void killBuild(BuildQueueManager buildQueueManager, CommonContext context, CurrentResult current) {
        DeploymentExecutionService deploymentExecutionService = getService(
                DeploymentExecutionService.class, "deploymentExecutionService");
        DeploymentResultService deploymentResultService = getService(
                DeploymentResultService.class, "deploymentResultService");

        if (context instanceof BuildContext) {
            current.setLifeCycleState(LifeCycleState.NOT_BUILT);
            buildQueueManager.removeBuildFromQueue(context.getResultKey());
        } else if (context instanceof DeploymentContext) {
            DeploymentContext dc = (DeploymentContext) context;
            ImpersonationHelper.runWithSystemAuthority((BambooRunnables.NotThrowing) () -> {
                //without runWithSystemAuthority() this call terminates execution with a log entry only
                DeploymentResult deploymentResult = deploymentResultService.getDeploymentResult(
                        dc.getDeploymentResultId());
                if (deploymentResult != null) {
                    deploymentExecutionService.stop(deploymentResult, null);
                }
            });
        } else {
            logger.error("unknown type of CommonContext {}", context.getClass());
        }
    }

    private <T> T getService(Class<T> type, String serviceKey) {
        final Object obj = checkNotNull(
                ContainerManager.getComponent(serviceKey), "Expected value for key '" + serviceKey + "', found nothing."
        );
        return type.cast(obj);
    }

    protected <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
        final Object obj = checkNotNull(jobDataMap.get(serviceKey),
                "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }
}
