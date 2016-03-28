/*
 * Copyright 2015 Atlassian.
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

package com.atlassian.buildeng.ecs;

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerOverride;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.StartTaskRequest;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskOverride;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.ImageNotRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.RevisionNotActiveException;
import com.atlassian.buildeng.ecs.scheduling.AWSSchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);
    private static final AtomicBoolean cacheValid = new AtomicBoolean(false);
    private final BandanaManager bandanaManager;
    private final AdministrationConfigurationAccessor admConfAccessor;
    private ConcurrentMap<String, Integer> dockerMappings = new ConcurrentHashMap<>();
    //TODO worth making these components?
    private final ECSScheduler ecsScheduler = new CyclingECSScheduler(new AWSSchedulerBackend());

    @Autowired
    public ECSIsolatedAgentServiceImpl(BandanaManager bandanaManager, AdministrationConfigurationAccessor admConfAccessor) {
        this.bandanaManager = bandanaManager;
        this.admConfAccessor = admConfAccessor;
        this.updateCache();
    }

    // Constructs a standard build agent task definition request with sidekick and generated task definition family
    private RegisterTaskDefinitionRequest taskDefinitionRequest(String dockerImage, String baseUrl) {
        return new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(
                        Constants.AGENT_BASE_DEFINITION
                            .withImage(dockerImage)
                            .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_SERVER).withValue(baseUrl))
                            .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_IMAGE).withValue(dockerImage)),
                        Constants.SIDEKICK_DEFINITION
                            .withImage(getCurrentSidekick()))
                .withFamily(Constants.TASK_DEFINITION_NAME);
    }

    // Constructs a standard de-register request for a standard generated task definition
    private static DeregisterTaskDefinitionRequest deregisterTaskDefinitionRequest(Integer revision) {
        return new DeregisterTaskDefinitionRequest().withTaskDefinition(Constants.TASK_DEFINITION_NAME + ":" + revision);
    }

    // Bandana + Caching

    private void updateCache() {
        if (cacheValid.compareAndSet(false, true)) {
            ConcurrentHashMap<String, Integer> values = (ConcurrentHashMap<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY);
            if (values != null) {
                this.dockerMappings = values;
            }
        }
    }

    private void invalidateCache() {
        cacheValid.set(false);
    }

    private AmazonECSClient createClient() {
        return new AmazonECSClient();
    }

    // Sidekick management

    /**
     * Get the repository that is currently configured to be used for the agent sidekick
     *
     * @return The current sidekick repository
     */
    String getCurrentSidekick() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_SIDEKICK_KEY);
        return name == null ? Constants.DEFAULT_SIDEKICK_REPOSITORY : name;
    }

    /**
     *  Set the agent sidekick to be used with isolated docker
     *
     * @param name The sidekick repository
     */
    Collection<Exception> setSidekick(String name) {
        Collection<Exception> exceptions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry: dockerMappings.entrySet()) {
            String dockerImage = entry.getKey();
            Integer revision = entry.getValue();
            try {
                deregisterDockerImage(revision);
                registerDockerImage(dockerImage);
            } catch (ImageAlreadyRegisteredException | RevisionNotActiveException | ECSException e) {
                exceptions.add(e);
            }
        }
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_SIDEKICK_KEY, name);
        return exceptions;
    }

    /**
     * Reset the agent sidekick to be used to the default
     */
    Collection<Exception> resetSidekick() {
        return setSidekick(Constants.DEFAULT_SIDEKICK_REPOSITORY);
    }

    // ECS Cluster management

    /**
     * Get the ECS cluster that is currently configured to be used
     *
     * @return The current cluster name
     */
    String getCurrentCluster() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_CLUSTER_KEY);
        return name == null ? Constants.DEFAULT_CLUSTER : name;
    }

    /**
     * Set the ECS cluster to run isolated docker agents on
     *
     * @param name The cluster name
     */
    void setCluster(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_CLUSTER_KEY, name);
    }

    /**
     * Get a collection of potential ECS clusters to use
     *
     * @return The collection of cluster names
     */
    List<String> getValidClusters() throws ECSException {
        AmazonECSClient ecsClient = createClient();
        try {
            ListClustersResult result = ecsClient.listClusters();
            return result.getClusterArns().stream().map((String x) -> x.split("/")[1]).collect(Collectors.toList());
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    private StartTaskRequest createStartTaskRequest(String resultId, Integer revision, @NotNull String containerInstanceArn) throws ECSException {
        ContainerOverride buildResultOverride = new ContainerOverride()
                .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_RESULT_ID).withValue(resultId))
                .withName(Constants.AGENT_CONTAINER_NAME);
        return new StartTaskRequest()
                .withCluster(getCurrentCluster())
                .withContainerInstances(containerInstanceArn)
                .withTaskDefinition(Constants.TASK_DEFINITION_NAME + ":" + revision)
                .withOverrides(new TaskOverride().withContainerOverrides(buildResultOverride));
    }


    // Isolated Agent Service methods
    @Override
    public IsolatedDockerAgentResult startAgent(IsolatedDockerAgentRequest req) throws ImageNotRegisteredException, ECSException {
        Integer revision = dockerMappings.get(req.getDockerImage());
        final IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
        String resultId = req.getBuildResultKey();
        AmazonECSClient ecsClient = createClient();
        if (revision == null) {
            throw new ImageNotRegisteredException(req.getDockerImage());
        }

        logger.info("Spinning up new docker agent from task definition {}:{} {}", Constants.TASK_DEFINITION_NAME, revision, req.getBuildResultKey());
        boolean finished = false;
        while (!finished) {
            try {
                String containerInstanceArn = null;
                try {
                     containerInstanceArn = ecsScheduler.schedule(getCurrentCluster(), Constants.TASK_MEMORY, Constants.TASK_CPU);
                } catch (ECSException e) {
                    logger.warn("Failed to schedule, treating as overload: " + String.valueOf(e));
                }
                if (containerInstanceArn == null) {
                    logger.info("ECS cluster is overloaded, waiting for auto-scaling and retrying");
                    finished = false; // Retry
                    Thread.sleep(5000); // 5 Seconds is a good amount of time.
                    continue;
                }
                StartTaskResult startTaskResult = ecsClient.startTask(createStartTaskRequest(resultId, revision, containerInstanceArn));
                startTaskResult.getTasks().stream().findFirst().ifPresent((Task t) -> {
                    toRet.withCustomResultData("TaskARN", t.getTaskArn());
                });
                logger.info("ECS Returned: {}", startTaskResult);
                List<Failure> failures = startTaskResult.getFailures();
                if (failures.size() == 1) {
                    String err = failures.get(0).getReason();
                    if (err.startsWith("RESOURCE")) {
                        logger.info("ECS cluster is overloaded, waiting for auto-scaling and retrying");
                        finished = false; // Retry
                        Thread.sleep(5000); // 5 Seconds is a good amount of time.
                    } else {
                        toRet.withError(mapRunTaskErrorToDescription(err));
                        finished = true; // Not a resource error, we don't handle
                    }
                } else {
                    for (Failure err : startTaskResult.getFailures()) {
                        toRet.withError(mapRunTaskErrorToDescription(err.getReason()));
                    }
                    finished = true; // Either 0 or many errors, either way we're done
                }
            } catch (Exception e) {
                throw new ECSException(e);
            }
        }
        return toRet;
    }

    // Docker - ECS mapping management

    /**
     * Synchronously register a docker image to be used with isolated docker builds
     *
     * @param dockerImage The image to register
     * @return The internal identifier for the registered image.
     */
    Integer registerDockerImage(String dockerImage) throws ImageAlreadyRegisteredException, ECSException {
        updateCache();
        if (dockerMappings.containsKey(dockerImage)) {
            throw new ImageAlreadyRegisteredException(dockerImage);
        }
        AmazonECSClient ecsClient = createClient();
        try {
            RegisterTaskDefinitionResult result = ecsClient.registerTaskDefinition(
                    taskDefinitionRequest(dockerImage, admConfAccessor.getAdministrationConfiguration().getBaseUrl()));
            Integer revision = result.getTaskDefinition().getRevision();
            dockerMappings.put(dockerImage, revision);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY, dockerMappings);
            invalidateCache();
            return revision;
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    /**
     * Synchronously deregister the docker image with given task revision
     *
     * @param revision The internal ECS task definition to deregister
     */
    void deregisterDockerImage(Integer revision) throws RevisionNotActiveException, ECSException {
        updateCache();
        if (!dockerMappings.containsValue(revision)) {
            throw new RevisionNotActiveException(revision);
        }
        AmazonECSClient ecsClient = createClient();

        try {
            ecsClient.deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
            dockerMappings.values().remove(revision);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY, dockerMappings);
            invalidateCache();
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    /**
     * @return All the docker image:identifier pairs this service has registered
     */
    Map<String, Integer> getAllRegistrations() {
        updateCache();
        return dockerMappings;
    }

    @Override
    public List<String> getKnownDockerImages() {
        List<String> toRet = new ArrayList<>(getAllRegistrations().keySet());
        // sort for sake of UI/consistency?
        Collections.sort(toRet);
        return toRet;
    }

    private String mapRunTaskErrorToDescription(String reason) {
        //http://docs.aws.amazon.com/AmazonECS/latest/developerguide/troubleshooting.html#api_failures_messages
        if ("AGENT".equals(reason)) {
            return "AGENT - The container instance that you attempted to launch a task onto has an agent which is currently disconnected. In order to prevent extended wait times for task placement, the request was rejected.";
        } else if ("ATTRIBUTE".equals(reason)) {
            return "ATTRIBUTE - Your task definition contains a parameter that requires a specific container instance attribute that is not available on your container instances.";
        } else if (reason.startsWith("RESOURCE")) {
            return reason + " - The resource or resources requested by the task are unavailable on the given container instance. If the resource is CPU or memory, you may need to add container instances to your cluster.";
        } else {
            return "Unknown RunTask reason:" + reason;
        }
    }
}
