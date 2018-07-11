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
import com.atlassian.buildeng.isolated.docker.events.DockerAgentKubeRestartEvent;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.AgentCreationRescheduler;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.buildeng.spi.isolated.docker.WatchdogJob;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.osgi.factory.descriptor.ComponentModuleDescriptor;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private static final int MAX_RETRY_COUNT = 30;
    
    private final ExecutorService executorService;
    private static final Logger logger = LoggerFactory.getLogger(KubernetesWatchdog.class);

    /**
     * creates new object of type KybernetesWatchdog.
     */
    public KubernetesWatchdog() {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(10, 10,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        tpe.allowCoreThreadTimeOut(true);
        executorService = tpe;
    }

    
    
    @Override
    public final void execute(Map<String, Object> jobDataMap) {
        long start = System.currentTimeMillis();
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) { 
            // this is throwable because of NoClassDefFoundError and alike.
            // These are not Exception subclasses and actually
            // throwing something here will stop rescheduling the job forever (until next redeploy)
            logger.error("Exception caught and swallowed to preserve rescheduling of the task", t);
        }
        logger.debug("Time overall {}", Duration.ofMillis(System.currentTimeMillis() - start));

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

        //AgentCreationRescheduler - this component cannot be injected by spring 
        // as it introduces cycles in spring injection between plugins.
        // Ugly but I haven't found a shortcut utility method do to the same in bamboo.
        PluginAccessor pluginAccessor = getService(PluginAccessor.class, "pluginAccessor");
        AgentCreationRescheduler acr = null;
        ModuleDescriptor md = pluginAccessor.getPluginModule(
                "com.atlassian.buildeng.bamboo-isolated-docker-plugin:agentCreationRescheduler");
        if (md instanceof ComponentModuleDescriptor) {
            ComponentModuleDescriptor cmd = (ComponentModuleDescriptor) md;
            Object o = cmd.getModule();
            if (o instanceof AgentCreationRescheduler) {
                acr = (AgentCreationRescheduler) o;
            }
        }
        AgentCreationRescheduler agentCreationRescheduler = acr;
        if (agentCreationRescheduler == null) {
            throw new IllegalStateException("Cannot find component "
                    + "com.atlassian.buildeng.bamboo-isolated-docker-plugin:agentCreationRescheduler");
        }

        KubernetesClient client = new KubernetesClient(globalConfiguration);
        long clusterStateQueryTime = System.currentTimeMillis();
        List<Pod> pods = client.getPods(
                PodCreator.LABEL_BAMBOO_SERVER, globalConfiguration.getBambooBaseUrlAskKubeLabel());
        logger.debug("Time it took query current pods {}", 
                Duration.ofMillis(System.currentTimeMillis() - clusterStateQueryTime));
        
        Map<String, TerminationReason> terminationReasons = getPodTerminationReasons(jobDataMap);
        List<Future<Optional<TerminationReason>>> killedFutures = new ArrayList<>();
        
        // delete pods which have had the bamboo-agent container terminated
        Set<BackoffCache> newBackedOff = new HashSet<>();
        
        List<TerminatePodSelector> selectors = Arrays.asList(
                new OutOfResourcesSelector(),
                new TerminatedAgentContainer(),
                new CreateContainerError(),
                new ContainerErrorStates());
        
        long killingStart = System.currentTimeMillis();
        for (Pod pod : pods) {
            // checking if the deletionTimestamp is set is the easiest way to determine if the pod is currently
            // being terminated, as there is no "Terminating" pod phase
            if (pod.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            
            selectors.stream()
                    .filter((TerminatePodSelector t) -> t.shouldBeDeleted(pod))
                    .findFirst()
                    .ifPresent((TerminatePodSelector t) -> {
                        killedFutures.add(executorService.submit(t.delete(pod, client)));
                    });
        }
        
        killedFutures.stream().forEach((Future<Optional<TerminationReason>> t) -> {
            try {
                Optional<TerminationReason> result = t.get();
                if (result.isPresent()) {
                    TerminationReason reason = result.get();
                    pods.remove(reason.getPod());
                    terminationReasons.put(KubernetesHelper.getName(reason.getPod()), reason);
                }
            } catch (InterruptedException ex) {
                logger.error("interrupted", ex);
            } catch (ExecutionException ex) {
                logger.error("Future Execution failed", ex);
            }
        });
        if (!killedFutures.isEmpty()) {
            logger.info("Deleting {} pods took:{} s", killedFutures.size(), 
                    Duration.ofMillis(System.currentTimeMillis() - killingStart).getSeconds());
        }
  
        for (Pod pod : pods) {
            // checking if the deletionTimestamp is set is the easiest way to determine if the pod is currently
            // being terminated, as there is no "Terminating" pod phase
            if (pod.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            //identify if pod is stuck in "imagePullBackOff' loop.
            newBackedOff.addAll(
                    Stream.concat(
                            pod.getStatus().getContainerStatuses().stream(),
                            pod.getStatus().getInitContainerStatuses().stream())
                    .filter((ContainerStatus t) -> t.getState().getWaiting() != null)
                    .filter((ContainerStatus t) -> 
                            "ImagePullBackOff".equals(t.getState().getWaiting().getReason())
                         || "ErrImagePull".equals(t.getState().getWaiting().getReason()))
                    .map((ContainerStatus t) -> 
                            new BackoffCache(KubernetesHelper.getName(pod), t.getName(),
                                    t.getState().getWaiting().getMessage(), t.getImage()))
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
                        Optional<TerminationReason> deleted = deletePod(
                                client, pod,
                                "Container '" + t.containerName + "' image '" + t.imageName + "' pull failed", false);
                        if (deleted.isPresent()) {
                            terminationReasons.put(KubernetesHelper.getName(deleted.get().getPod()), deleted.get());
                            pods.remove(pod);
                        }
                    } else {
                        logger.warn("Could not find pod {} in the current list.", t.podName);
                    }
                });
        

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
                    //we don't use System.currentTimeMillis but clusterStateQueryTime here because our data is based
                    //on a query that could have happened significantly in the past. The pod query itself takes time
                    //and every pod deletion equals to 2 kubectl commands and thus in this loop we can accumulate
                    //significant time penalties that don't fit MISSING_POD_GRACE_PERIOD_MINUTES nicely.
                    //Ideally we would also not compare to QUEUE_TIMESTAMP but to the time when 'kubectl create pod' was
                    //actually executed in KubernetesIsolatedDockerImpl (we create pods concurrently, still 
                    //can incur some time penalty there.
                    Duration grace = Duration.ofMillis(clusterStateQueryTime - queueTime);
                    if (terminationReasons.get(podName) != null
                            || grace.toMinutes() >= MISSING_POD_GRACE_PERIOD_MINUTES) {

                        String errorMessage;
                        TerminationReason reason = terminationReasons.get(podName);
                        if (reason != null && reason.isRestartPod() 
                                && getRetryCount(reason.getPod()) < MAX_RETRY_COUNT) {
                            retryPodCreation(context, reason.getPod(), reason.getErrorMessage(), podName,
                                    getRetryCount(reason.getPod()), eventPublisher, agentCreationRescheduler,
                                    globalConfiguration);
                        } else {
                            if (reason != null) {
                                String logMessage = "Build was not queued due to pod deletion: " + podName;
                                logger.info("Stopping job {} because pod {} no longer exists (grace timeout {})",
                                        context.getResultKey(), podName, grace);
                                errorUpdateHandler.recordError(context.getEntityKey(), logMessage);
                                errorMessage = reason.getErrorMessage();
                                logger.error("{}\n{}\n{}",
                                        logMessage, errorMessage, terminationReasons.get(podName).getDescribePod());
                                current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                                generateRemoteFailEvent(context, errorMessage, podName, 
                                        isolatedAgentService, eventPublisher, globalConfiguration);
                                killBuild(deploymentExecutionService, deploymentResultService, logger, 
                                        buildQueueManager, context, current);
                            } else {
                                errorMessage = "Termination reason unknown, pod deleted by Kubernetes infrastructure.";
                                retryPodCreation(context, null, errorMessage,
                                        podName, 0, eventPublisher, agentCreationRescheduler, globalConfiguration);
                            }
                        }
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

                        Optional<TerminationReason> deleted = deletePod(client, pod, errorMessage, false);
                        if (deleted.isPresent()) {
                            logger.error("{}\n{}", logMessage, deleted.get().getDescribePod());
                        }

                        current.getCustomBuildData().put(RESULT_ERROR, errorMessage);
                        generateRemoteFailEvent(context, errorMessage, podName, isolatedAgentService,
                                eventPublisher, globalConfiguration);

                        killBuild(deploymentExecutionService, deploymentResultService, logger, buildQueueManager,
                                context, current);
                    }
                }
            }
        });
    }
    
    private int getRetryCount(Pod pod) {
        String retry = pod.getMetadata().getAnnotations()
                .getOrDefault(PodCreator.ANN_RETRYCOUNT, "0");
        return Integer.parseInt(retry);
    }

    private void retryPodCreation(CommonContext context, Pod pod, 
            String errorMessage, String podName, int retryCount, EventPublisher eventPublisher, 
            AgentCreationRescheduler rescheduler, GlobalConfiguration configuration) {
        Configuration config = AccessConfiguration.forContext(context);
        //when pod is not around, just generate new UUID :(
        String uuid = pod != null ? pod.getMetadata().getAnnotations().getOrDefault(PodCreator.ANN_UUID,
                pod.getMetadata().getLabels().get(PodCreator.ANN_UUID)) : UUID.randomUUID().toString();
        context.getCurrentResult().getCustomBuildData().remove(KubernetesIsolatedDockerImpl.RESULT_PREFIX
                    + KubernetesIsolatedDockerImpl.NAME);
        rescheduler.reschedule(new RetryAgentStartupEvent(config, context,
                retryCount + 1, UUID.fromString(uuid)));
        eventPublisher.publish(new DockerAgentKubeRestartEvent(
                errorMessage, context.getResultKey(), podName, Collections.emptyMap(), configuration));
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
                                         IsolatedAgentService isolatedAgentService,
                                         EventPublisher eventPublisher, GlobalConfiguration configuration) {
        Configuration config = AccessConfiguration.forContext(context);
        Map<String, String> customData = new HashMap<>(context.getCurrentResult().getCustomBuildData());
        customData.entrySet().removeIf(
            (Map.Entry<String, String> tt) -> !tt.getKey().startsWith(KubernetesIsolatedDockerImpl.RESULT_PREFIX));
        Map<String, URL> containerLogs = isolatedAgentService.getContainerLogs(config, customData);

        eventPublisher.publish(new DockerAgentKubeFailEvent(
                reason, context.getResultKey(), podName, containerLogs, configuration));
    }

    private static Optional<TerminationReason> deletePod(KubernetesClient client, Pod pod, String terminationReason,
            boolean restartPod) {
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
            return Optional.empty();
        }

        logger.debug("Pod {} successfully deleted. Final state:\n{}", KubernetesHelper.getName(pod), describePod);
        return Optional.of(new TerminationReason(pod, new Date(), terminationReason, describePod, restartPod));
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
        private final Pod pod;
        private final boolean restartPod;

        public TerminationReason(Pod pod, Date terminationTime, String errorMessage, String describePod,
                boolean restartPod) {
            this.terminationTime = terminationTime;
            this.errorMessage = errorMessage;
            this.describePod = describePod;
            this.pod = pod;
            this.restartPod = restartPod;
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

        public Pod getPod() {
            return pod;
        }

        public boolean isRestartPod() {
            return restartPod;
        }
    }
    
    private static interface TerminatePodSelector {
        
        boolean shouldBeDeleted(Pod pod);
        
        Callable<Optional<TerminationReason>> delete(Pod pod, KubernetesClient client);
        
    }
    
    private static class OutOfResourcesSelector implements TerminatePodSelector {

        @Override
        public boolean shouldBeDeleted(Pod pod) {
            return "Failed".equals(pod.getStatus().getPhase())
                    && ("OutOfcpu".equals(pod.getStatus().getReason()) 
                     || "OutOfmemory".equals(pod.getStatus().getReason()));
        }

        @Override
        public Callable<Optional<TerminationReason>> delete(Pod pod, KubernetesClient client) {
            logger.info("Killing pod {} due to resource constraints. {} ",
                    KubernetesHelper.getName(pod), pod.getStatus());
            String message = pod.getStatus().getReason();
            return () -> deletePod(
                    client, pod, "Bamboo agent could not be scheduled "
                            + (message != null ? ":" + message : ""), true);
        }
        
    }
    
    private static class TerminatedAgentContainer implements TerminatePodSelector {

        @Override
        public boolean shouldBeDeleted(Pod pod) {
            ContainerStatus agentContainer = pod.getStatus().getContainerStatuses().stream()
                    .filter((ContainerStatus t) -> t.getName().equals(PodCreator.CONTAINER_NAME_BAMBOOAGENT))
                    .findFirst().orElse(null);
            // if agentContainer == null then the pod is still initializing
            return agentContainer != null && agentContainer.getState().getTerminated() != null;
        }

        @Override
        public Callable<Optional<TerminationReason>> delete(Pod pod, KubernetesClient client) {
            logger.info("Killing pod {} with terminated agent container. Container states: {}",
                    KubernetesHelper.getName(pod),
                    pod.getStatus().getContainerStatuses().stream()
                            .map(t -> t.getState()).collect(Collectors.toList()));
            String message = pod.getStatus().getContainerStatuses().stream()
                    .filter((ContainerStatus t) -> t.getName().equals(PodCreator.CONTAINER_NAME_BAMBOOAGENT))
                    .findFirst().get().getState().getTerminated().getMessage();
            return () -> deletePod(
                    client, pod, "Bamboo agent container prematurely exited" 
                            + (message != null ? ":" + message : ""), false);
        }
        
    }
    
    private static class CreateContainerError implements TerminatePodSelector {

        @Override
        public boolean shouldBeDeleted(Pod pod) {
            return Stream.concat(pod.getStatus().getInitContainerStatuses().stream(), 
                                 pod.getStatus().getContainerStatuses().stream())
                    .filter((ContainerStatus t) -> t.getState().getWaiting() != null)
                    .filter((ContainerStatus t) -> "CreateContainerError".equals(t.getState().getWaiting().getReason()))
                    .findFirst().isPresent();
        }

        @Override
        public Callable<Optional<TerminationReason>> delete(Pod pod, KubernetesClient client) {
            logger.info("Killing pod {} with CreateContainerError.",
                    KubernetesHelper.getName(pod));
            return () -> deletePod(
                    client, pod, "Pod with CreateContainerError.", true);
        }
        
    }
    
    private static class ContainerErrorStates implements TerminatePodSelector {

        private List<String> errorStates(Pod pod) {
            return Stream.concat(
                    waitingStateErrorsStream(pod),
                    terminatedStateErrorsStream(pod)).collect(Collectors.toList());
        }
        
        @Override
        public boolean shouldBeDeleted(Pod pod) {
            return !errorStates(pod).isEmpty();
        }

        @Override
        public Callable<Optional<TerminationReason>> delete(Pod pod, KubernetesClient client) {
            List<String> errorStates = errorStates(pod);
            logger.info("Killing pod {} with error state. Container states: {}",
                    KubernetesHelper.getName(pod), errorStates);
            // ImageInspectError:Failed to inspect image "xxx": 
            //          rpc error: code = Unknown desc = Error response from daemon: readlink /var/lib/docker/overlay2:
            //          invalid argument
            //this is a retryable error, only appears to affect single node fairly rarely.
            //if there are others that can create endless cycles, we need to revisit
            boolean retry = errorStates.stream().anyMatch((String t) -> t.contains("ImageInspectError"));
            return () -> deletePod(
                    client, pod, "Container error state(s):" + errorStates, retry);
        }
        
        private Stream<String> waitingStateErrorsStream(Pod pod) {
            return Stream.concat(
                        pod.getStatus().getContainerStatuses().stream(),
                        pod.getStatus().getInitContainerStatuses().stream())
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
        
    }
}
