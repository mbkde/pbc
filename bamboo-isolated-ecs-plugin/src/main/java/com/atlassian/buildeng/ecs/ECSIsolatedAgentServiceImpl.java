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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.VolumeFrom;
import com.atlassian.bamboo.agent.elastic.server.ElasticAccountBean;
import com.atlassian.bamboo.agent.elastic.server.ElasticConfiguration;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.fugue.Either;
import com.atlassian.fugue.Maybe;
import com.atlassian.fugue.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);
    private final ElasticAccountBean elasticAccountBean;
    private BandanaManager bandanaManager;
    private ConcurrentMap<String, Integer> dockerMappings = new ConcurrentHashMap<>();
    private static AtomicBoolean cacheValid = new AtomicBoolean(false);

    // Bandana access keys
    private static final String BANDANA_CLUSTER_KEY = "com.atlassian.buildeng.ecs.cluster";
    private static final String BANDANA_DOCKER_MAPPING_KEY = "com.atlassian.buildeng.ecs.docker";

    // The name of the sidekick docker image and sidekick container
    private static final String SIDEKICK_NAME = "bamboo-agent-sidekick";

    // The name of the agent container
    private static final String AGENT_NAME = "bamboo-agent";

    // The name used for the generated task definition (a.k.a. family)
    private static final String TASK_DEFINITION_NAME = "staging-bamboo-generated";

    // The name of the atlassian docker registry
    private static final String ATLASSIAN_REGISTRY = "docker.atlassian.io";

    // The default cluster to use
    private static final String DEFAULT_CLUSTER = "staging_bamboo";

    // The container definition of the sidekick
    private static final ContainerDefinition sidekickDefinition =
            new ContainerDefinition()
                    .withName(SIDEKICK_NAME)
                    .withImage(ATLASSIAN_REGISTRY + "/" + SIDEKICK_NAME)
                    .withCpu(10)
                    .withMemory(512);

    // The container definition of the standard spec build agent, sans docker image name
    private static final ContainerDefinition agentBaseDefinition =
            new ContainerDefinition()
                    .withName(AGENT_NAME)
                    .withCpu(900)
                    .withMemory(3072)
                    .withVolumesFrom(new VolumeFrom().withSourceContainer(SIDEKICK_NAME));

    // Constructs a standard build agent container definition with the given docker image name
    private static ContainerDefinition agentDefinition(String dockerImage) {
        return agentBaseDefinition.withImage(dockerImage);
    }

    // Constructs a standard build agent task definition request with sidekick and generated task definition family
    private static RegisterTaskDefinitionRequest taskDefinitionRequest(String dockerImage) {
        return new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(agentDefinition(dockerImage), sidekickDefinition)
                .withFamily(TASK_DEFINITION_NAME);
    }

    // Constructs a standard de-register request for a standard generated task definition
    private static DeregisterTaskDefinitionRequest deregisterTaskDefinitionRequest(Integer revision) {
        return new DeregisterTaskDefinitionRequest().withTaskDefinition(TASK_DEFINITION_NAME + ":" + revision);
    }

    @Autowired
    public ECSIsolatedAgentServiceImpl(ElasticAccountBean elasticAccountBean, BandanaManager bandanaManager) {
        this.elasticAccountBean = elasticAccountBean;
        this.bandanaManager = bandanaManager;
        this.updateCache();
    }

    // Bandana + Caching

    private void updateCache() {
        if (cacheValid.compareAndSet(false, true)) {
            ConcurrentHashMap<String, Integer> values = (ConcurrentHashMap<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DOCKER_MAPPING_KEY);
            if (values != null) {
                this.dockerMappings = values;
            }
        }
    }

    private void invalidateCache() {
        cacheValid.set(false);
    }

    private AmazonECSClient createClient() throws Exception {
        final ElasticConfiguration elasticConfig = elasticAccountBean.getElasticConfig();
        if (elasticConfig != null) {
            AWSCredentials awsCredentials = new BasicAWSCredentials(elasticConfig.getAwsAccessKeyId(),
                    elasticConfig.getAwsSecretKey());
            return new AmazonECSClient(awsCredentials);
        } else {
            throw new Exception("No AWS credentials, aborting.");
        }
    }

    // ECS Cluster management

    /**
     * Get the ECS cluster that is currently configured to be used
     *
     * @return The current cluster name
     */
    String getCurrentCluster() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CLUSTER_KEY);
        return name == null ? DEFAULT_CLUSTER : name;
    }

    /**
     * Set the ECS cluster to run isolated docker agents on
     *
     * @param name The cluster name
     */
    void setCluster(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CLUSTER_KEY, name);
    }

    /**
     * Get a collection of potential ECS clusters to use
     *
     * @return The collection of cluster names
     */
    Either<String, List<String>> getValidClusters() {
        try {
            AmazonECSClient ecsClient = createClient();
            ListClustersResult result = ecsClient.listClusters();
            return Either.right(result.getClusterArns().stream().map((String x) -> x.split("/")[1]).collect(Collectors.toList()));
        } catch (Exception e) {
            return Either.left(e.toString());
        }
    }

    // Isolated Agent Service methods

    @Override
    public IsolatedDockerAgentResult startAgent(IsolatedDockerAgentRequest req) {
        Integer revision = dockerMappings.get(req.getDockerImage());
        IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();

        if (revision == null) {
            toRet.getErrors().add(String.format("Docker image: '%s' is not registered", req.getDockerImage()));
        } else {
            try {
                AmazonECSClient ecsClient = createClient();
                logger.info("Spinning up new docker agent from task definition %s:%d %s", TASK_DEFINITION_NAME, revision, req.getBuildResultKey());
                RunTaskRequest runTaskRequest = new RunTaskRequest()
                        .withCluster(getCurrentCluster())
                        .withTaskDefinition(TASK_DEFINITION_NAME + ":" + revision)
                        .withCount(1);
                RunTaskResult runTaskResult = ecsClient.runTask(runTaskRequest);
                logger.info("ECS Returned: {}", runTaskResult.toString());
                for (Failure err : runTaskResult.getFailures()) {
                    toRet = toRet.withError(err.getReason());
                }
            } catch (Exception e) {
                toRet.getErrors().add(e.toString());
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
    Either<String, Integer> registerDockerImage(String dockerImage) {
        updateCache();
        if (dockerMappings.containsKey(dockerImage)) {
            return Either.left(String.format("Docker image '%s' is already registered.", dockerImage));
        } else {
            try {
                AmazonECSClient ecsClient = createClient();
                RegisterTaskDefinitionResult result = ecsClient.registerTaskDefinition(taskDefinitionRequest(dockerImage));
                Integer revision = result.getTaskDefinition().getRevision();
                dockerMappings.put(dockerImage, revision);
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DOCKER_MAPPING_KEY, dockerMappings);
                invalidateCache();
                return Either.right(revision);
            } catch (Exception e) {
                return Either.left(e.toString());
            }
        }
    }

    /**
     * Synchronously deregister the docker image with given task revision
     *
     * @param revision The internal ECS task definition to deregister
     */
    Maybe<String> deregisterDockerImage(Integer revision) {
        updateCache();
        if (dockerMappings.containsValue(revision)) {
            try {
                AmazonECSClient ecsClient = createClient();
                ecsClient.deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
                dockerMappings.values().remove(revision);
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DOCKER_MAPPING_KEY, dockerMappings);
                invalidateCache();
                return Option.none();
            } catch (Exception e) {
                return Option.option(e.toString());
            }
        } else {
            return Option.option(String.format("Revision %d is not available", revision));
        }
    }

    /**
     * @return All the docker image:identifier pairs this service has registered
     */
    Map<String, Integer> getAllRegistrations() {
        updateCache();
        return dockerMappings;
    }
}
