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
import com.amazonaws.services.ecs.model.*;
import com.atlassian.bamboo.agent.elastic.server.ElasticAccountBean;
import com.atlassian.bamboo.agent.elastic.server.ElasticConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.fugue.Either;
import com.atlassian.fugue.Maybe;
import com.atlassian.fugue.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);
    private final ElasticAccountBean elasticAccountBean;

    // The name of the sidekick docker image and sidekick container
    private static final String sidekickName = "bamboo-agent-sidekick";

    // The name of the agent container
    private static final String agentName = "bamboo-agent";

    // The name used for the generated task definition (a.k.a. family)
    private static final String taskDefinitionName = "staging-bamboo-generated";

    // The name of the atlassian docker registry
    private static final String atlassianRegistry = "docker.atlassian.io";

    // The container definition of the sidekick
    private static final ContainerDefinition sidekickDefinition =
            new ContainerDefinition()
                    .withName(sidekickName)
                    .withImage(atlassianRegistry + "/" + sidekickName)
                    .withCpu(10)
                    .withMemory(512);

    // The container definition of the standard spec build agent, sans docker image name
    private static final ContainerDefinition agentBaseDefinition =
            new ContainerDefinition()
                    .withName(agentName)
                    .withCpu(900)
                    .withMemory(3072)
                    .withVolumesFrom(new VolumeFrom().withSourceContainer(sidekickName));

    // Constructs a standard build agent container definition with the given docker image name
    private static ContainerDefinition agentDefinition(String dockerImage) {
        return agentBaseDefinition.withImage(dockerImage);
    }

    // Constructs a standard build agent task definition request with sidekick and generated task definition family
    private static RegisterTaskDefinitionRequest taskDefinitionRequest(String dockerImage) {
        return new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(agentDefinition(dockerImage), sidekickDefinition)
                .withFamily(taskDefinitionName);
    }

    // Constructs a standard de-register request for a standard generated task definition
    private static DeregisterTaskDefinitionRequest deregisterTaskDefinitionRequest(Integer revision) {
        return new DeregisterTaskDefinitionRequest().withTaskDefinition(taskDefinitionName + ":" + revision);
    }

    @Autowired
    public ECSIsolatedAgentServiceImpl(ElasticAccountBean elasticAccountBean) {
        this.elasticAccountBean = elasticAccountBean;
    }

    private AmazonECSClient createClient () throws Exception {
        final ElasticConfiguration elasticConfig = elasticAccountBean.getElasticConfig();
        if (elasticConfig != null) {
            AWSCredentials awsCredentials = new BasicAWSCredentials(elasticConfig.getAwsAccessKeyId(),
                    elasticConfig.getAwsSecretKey());
            return new AmazonECSClient(awsCredentials);
        } else {
            throw new Exception("No AWS credentials, aborting.");
        }
    }

    @Override
    public IsolatedDockerAgentResult startInstance(IsolatedDockerAgentRequest req) throws Exception {

        IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();

        try {
            AmazonECSClient ecsClient = createClient();
            logger.info("Spinning up new docker agent from task definition " + req.getTaskDefinition() + " " + req.getBuildResultKey());
            RunTaskRequest runTaskRequest = new RunTaskRequest()
                .withCluster(req.getCluster())
                .withTaskDefinition(req.getTaskDefinition())
                .withCount(1);
            RunTaskResult runTaskResult = ecsClient.runTask(runTaskRequest);
            logger.info("ECS Returned: {}", runTaskResult.toString());
            if (!runTaskResult.getFailures().isEmpty()) {
                for (Failure err : runTaskResult.getFailures()) {
                    toRet = toRet.withError(err.getReason());
                }
            }
        } catch (Exception exc) {
//                logger.error("Exception thrown", exc);
//TODO any exception wrapping necessary?
            throw exc;
        }

        return toRet;
    }

    @Override
    public Either<String, Integer> registerDockerImage(String dockerImage) {
        try {
            AmazonECSClient ecsClient = createClient();
            RegisterTaskDefinitionResult result = ecsClient.registerTaskDefinition(taskDefinitionRequest(dockerImage));
            return Either.right(result.getTaskDefinition().getRevision());
        } catch (Exception e) {
            return Either.left(e.toString());
        }
    }

    @Override
    public Maybe<String> deregisterDockerImage(Integer revision) {
        try {
            AmazonECSClient ecsClient = createClient();
            ecsClient.deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
            return Option.none();
        } catch (Exception e) {
            return Option.option(e.toString());
        }
    }
}
