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
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.ImageNotRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.RevisionNotActiveException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
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
                            .withEnvironment(new KeyValuePair().withName(Constants.SERVER_ENV_VAR).withValue(baseUrl))
                            .withEnvironment(new KeyValuePair().withName(Constants.IMAGE_ENV_VAR).withValue(dockerImage)),
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
    void setSidekick(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_SIDEKICK_KEY, name);
    }

    /**
     * Reset the agent sidekick to be used to the default
     */
    void resetSidekick() {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_SIDEKICK_KEY, Constants.DEFAULT_SIDEKICK_REPOSITORY);
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

    // Isolated Agent Service methods

    @Override
    public IsolatedDockerAgentResult startAgent(IsolatedDockerAgentRequest req) throws ImageNotRegisteredException, ECSException {
        Integer revision = dockerMappings.get(req.getDockerImage());
        IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
        AmazonECSClient ecsClient = createClient();
        if (revision == null) {
            throw new ImageNotRegisteredException(req.getDockerImage());
        }
        RunTaskRequest runTaskRequest = new RunTaskRequest()
                .withCluster(getCurrentCluster())
                .withTaskDefinition(Constants.TASK_DEFINITION_NAME + ":" + revision)
                .withCount(1);
        boolean finished = false;
        while (!finished) {
            try {
                logger.info("Spinning up new docker agent from task definition %s:%d %s", Constants.TASK_DEFINITION_NAME, revision, req.getBuildResultKey());
                RunTaskResult runTaskResult = ecsClient.runTask(runTaskRequest);
                logger.info("ECS Returned: {}", runTaskResult.toString());
                List<Failure> failures = runTaskResult.getFailures();
                if (failures.size() == 1) {
                    String err = failures.get(0).getReason();
                    if (err.startsWith("RESOURCE")) {
                        logger.info("ECS cluster is overloaded, waiting for auto-scaling and retrying");
                        finished = false; // Retry
                        Thread.sleep(5000); // 5 Seconds is a good amount of time.
                    } else {
                        toRet = toRet.withError(err);
                        finished = true; // Not a resource error, we don't handle
                    }
                } else {
                    for (Failure err : runTaskResult.getFailures()) {
                        toRet = toRet.withError(err.getReason());
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
}
