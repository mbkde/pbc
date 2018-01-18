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
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Background job checking the state of the cluster.
 */
public class KubernetesWatchdog extends WatchdogJob {
    private static final String RESULT_ERROR = "custom.isolated.docker.error";
    private static final String QUEUE_TIMESTAMP = "pbcJobQueueTime";
    private static final Long MAX_QUEUE_TIME_MINUTES = 60L;
    private static final String KEY_TERMINATED_POD_REASONS = "TERMINATED_PODS_MAP";
    private static final int MISSING_POD_GRACE_PERIOD_MINUTES = 1;
    private static final int MAX_BACKOFF_SECONDS = 600;

    private static final Logger logger = LoggerFactory.getLogger(KubernetesWatchdog.class);

    @Override
    public final void execute(Map<String, Object> jobDataMap) {
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) { 
            // this is throwable because of NoClassDefFoundError and alike.
            // These are not Exception subclasses and actually
            // throwing something here will stop rescheduling the job forever (until next redeploy)
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
        Map<String, TerminationReason> terminationReasons = getPodTerminationReasons(jobDataMap);
        List<Pod> killed = new ArrayList<>();
        
        // delete pods which have had the bamboo-agent container terminated
        Set<BackoffCache> newBackedOff = new HashSet<>();
        for (Pod pod : pods) {
            // checking if the deletionTimestamp is set is the easiest way to determine if the pod is currently
            // being terminated, as there is no "Terminating" pod phase
            if (pod.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            boolean deleted = false;
            Map<String, ContainerStatus> currentContainers = pod.getStatus().getContainerStatuses().stream()
                    .collect(Collectors.toMap(ContainerStatus::getName, x -> x));
            ContainerStatus agentContainer = currentContainers.get(PodCreator.CONTAINER_NAME_BAMBOOAGENT);
            // if agentContainer == null then the pod is still initializing
            if (agentContainer != null && agentContainer.getState().getTerminated() != null) {
                logger.info("Killing pod {} with terminated agent container. Container states: {}",
                        KubernetesHelper.getName(pod),
                        pod.getStatus().getContainerStatuses().stream()
                                .map(t -> t.getState()).collect(Collectors.toList()));
                String message = agentContainer.getState().getTerminated().getMessage();
                deleted = deletePod(
                        client, pod, terminationReasons, "Bamboo agent container prematurely exited" 
                                + (message != null ? ":" + message : ""));
                if (deleted) {
                    killed.add(pod);
                }
            }
            if (!deleted) {
                List<String> errorStates = Stream.concat(
                        waitingStateErrorsStream(pod),
                        terminatedStateErrorsStream(pod)).collect(Collectors.toList());
                if (!errorStates.isEmpty()) {
                    logger.info("Killing pod {} with error state. Container states: {}",
                            KubernetesHelper.getName(pod), errorStates);
                    deleted = deletePod(
                            client, pod, terminationReasons, "Container error state(s):" + errorStates);
                    if (deleted) {
                        killed.add(pod);
                    }
                }
            }
            if (!deleted) {
                //identify if pod is stuck in "imagePullBackOff' loop.
                newBackedOff.addAll(pod.getStatus().getContainerStatuses().stream()
                        .filter((ContainerStatus t) -> t.getState().getWaiting() != null)
                        .filter((ContainerStatus t) -> 
                                "ImagePullBackOff".equals(t.getState().getWaiting().getReason())
                             || "ErrImagePull".equals(t.getState().getWaiting().getReason()))
                        .map((ContainerStatus t) -> 
                                new BackoffCache(KubernetesHelper.getName(pod), t.getName(),
                                        t.getState().getWaiting().getMessage(), t.getImage()))
                        .collect(Collectors.toList()));
            }
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
                            "Container '" + t.containerName + "' image '" + t.imageName + "' pull failed");
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
                    Duration grace = Duration.ofMillis(System.currentTimeMillis() - queueTime);
                    if (terminationReasons.get(podName) != null
                            || grace.toMinutes() >= MISSING_POD_GRACE_PERIOD_MINUTES) {
                        String logMessage = "Build was not queued due to pod deletion: " + podName;
                        logger.info("Stopping job {} because pod {} no longer exists (grace timeout {})",
                                context.getResultKey(), podName, grace);
                        errorUpdateHandler.recordError(context.getEntityKey(), logMessage);

                        String errorMessage;
                        TerminationReason reason = terminationReasons.get(podName);
                        if (reason != null) {
                            errorMessage = reason.getErrorMessage();
                            logger.error("{}\n{}\n{}",
                                    logMessage, errorMessage, terminationReasons.get(podName).getDescribePod());
                        } else {
                            errorMessage = "Termination reason unknown, pod deleted by Kubernetes infrastructure.";
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
                        String logMessage = "Build was not queued after " + MAX_QUEUE_TIME_MINUTES + " minutes."
                                + " podName: " + podName;
                        errorUpdateHandler.recordError(context.getEntityKey(), logMessage);
                        String errorMessage = "Build terminated for queuing for too long";

                        boolean deleted = deletePod(client, pod, terminationReasons, errorMessage);
                        if (deleted) {
                            logger.error("{}\n{}", logMessage, terminationReasons.get(podName).getDescribePod());
                        }

                        current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                        generateRemoteFailEvent(context, errorMessage, podName, isolatedAgentService, eventPublisher);

                        killBuild(deploymentExecutionService, deploymentResultService, logger, buildQueueManager,
                                context, current);
                    }
                }
            }
        });
    }

    private Stream<String> waitingStateErrorsStream(Pod pod) {
        return pod.getStatus().getContainerStatuses().stream()
                .filter((ContainerStatus t) -> t.getState().getWaiting() != null)
                .filter((ContainerStatus t) ->
                        "ImageInspectError".equals(t.getState().getWaiting().getReason())
                                || "ErrInvalidImageName".equals(t.getState().getWaiting().getReason())
                                || "InvalidImageName".equals(t.getState().getWaiting().getReason()))
                .map((ContainerStatus t) -> t.getName() + ":" +  t.getState().getWaiting().getReason() + ":"
                        + (StringUtils.isBlank(t.getState().getWaiting().getMessage())
                                ? "<no details>" : t.getState().getWaiting().getMessage()));
    }
    
    private Stream<String> terminatedStateErrorsStream(Pod pod) {
        return pod.getStatus().getContainerStatuses().stream()
                .filter((ContainerStatus t) -> t.getState().getTerminated() != null)
                .filter((ContainerStatus t) ->
                        "Error".equals(t.getState().getTerminated().getReason()))
                .map((ContainerStatus t) -> t.getName() + ":" +  t.getState().getTerminated().getReason() + ":"
                        + "ExitCode:" + t.getState().getTerminated().getExitCode() + " - "
                        + (StringUtils.isBlank(t.getState().getTerminated().getMessage())
                                ? "<no details>" : t.getState().getTerminated().getMessage()));
    }
    
    
    Set<BackoffCache> getImagePullBackOffCache(Map<String, Object> jobDataMap) {
        @SuppressWarnings("unchecked")
        Set<BackoffCache> list = (Set<BackoffCache>) jobDataMap.get("ImagePullBackOffCache");
        if (list == null) {
            list = new HashSet<>();
            jobDataMap.put("ImagePullBackOffCache", list);
        }
        return list;
    }

    private void generateRemoteFailEvent(CommonContext context, String reason, String podName,
                                         IsolatedAgentService isolatedAgentService, EventPublisher eventPublisher) {
        Configuration config = AccessConfiguration.forContext(context);
        Map<String, String> customData = new HashMap<>(context.getCurrentResult().getCustomBuildData());
        customData.entrySet().removeIf(
            (Map.Entry<String, String> tt) -> !tt.getKey().startsWith(KubernetesIsolatedDockerImpl.RESULT_PREFIX));
        Map<String, URL> containerLogs = isolatedAgentService.getContainerLogs(config, customData);

        eventPublisher.publish(new DockerAgentKubeFailEvent(
                reason, context.getEntityKey(), podName, containerLogs));
    }

    private boolean deletePod(
            KubernetesClient client, Pod pod,
            Map<String, TerminationReason> terminationReasons, String terminationReason) {
        String describePod;
        try {
            describePod = client.describePod(pod);
        } catch (IOException | KubernetesClient.KubectlException | InterruptedException e) {
            describePod = String.format("Could not describe pod %s. %s", KubernetesHelper.getName(pod), e.toString());
            logger.error(describePod);
        }

        try {
            client.deletePod(pod);
        } catch (InterruptedException | IOException | KubernetesClient.KubectlException e) {
            logger.error("Failed to delete pod {}. {}", KubernetesHelper.getName(pod), e);
            return false;
        }

        logger.debug("Pod {} successfully deleted. Final state:\n{}", KubernetesHelper.getName(pod), describePod);
        terminationReasons.put(
                KubernetesHelper.getName(pod),
                new TerminationReason(new Date(), terminationReason, describePod));
        return true;
    }

    private Map<String, TerminationReason> getPodTerminationReasons(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        Map<String, TerminationReason> map = (Map<String, TerminationReason>) data.get(KEY_TERMINATED_POD_REASONS);
        if (map == null) {
            map = new HashMap<>();
            data.put(KEY_TERMINATED_POD_REASONS, map);
        }
        // trim values that are too old to have
        map.entrySet().removeIf(next -> Duration.ofMillis(System.currentTimeMillis() - next.getValue()
                        .getTerminationTime().getTime()).toMinutes() >= MISSING_POD_GRACE_PERIOD_MINUTES);
        return map;
    }

    private static class BackoffCache {
        private final String podName;
        private final Date creationTime;
        private final String containerName;
        private final String message;
        private final String imageName;
        
        BackoffCache(String podName, String containerName, String message, String imageName) {
            this.podName = podName;
            this.creationTime = new Date();
            this.containerName = containerName;
            this.message = message;
            this.imageName = imageName;
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

    private static final class TerminationReason {
        private final Date terminationTime;
        private final String errorMessage;
        private final String describePod;

        public TerminationReason(Date terminationTime, String errorMessage, String describePod) {
            this.terminationTime = terminationTime;
            this.errorMessage = errorMessage;
            this.describePod = describePod;
        }

        public Date getTerminationTime() {
            return terminationTime;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getDescribePod() {
            return describePod;
        }
    }
}
