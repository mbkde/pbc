package com.atlassian.buildeng.ecs;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.*;
import com.atlassian.bamboo.agent.elastic.server.ElasticAccountBean;
import com.atlassian.bamboo.agent.elastic.server.ElasticConfiguration;
import com.atlassian.fugue.Either;
import com.atlassian.fugue.Maybe;
import com.atlassian.fugue.Option;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created by obrent on 1/02/2016.
 */
public class ECSHandler {
    private AmazonECSClient ecsClient;

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
    public ECSHandler(ElasticAccountBean elasticAccountBean) {
        ElasticConfiguration elasticConfig = elasticAccountBean.getElasticConfig();
        assert elasticConfig != null;
        this.ecsClient = new AmazonECSClient(new BasicAWSCredentials(elasticConfig.getAwsAccessKeyId(), elasticConfig.getAwsSecretKey()));
    }

    public Either<String, Integer> registerTaskDefinition(String dockerImage) {
        try {
            RegisterTaskDefinitionResult result = ecsClient.registerTaskDefinition(taskDefinitionRequest(dockerImage));
            return Either.right(result.getTaskDefinition().getRevision());
        } catch (Exception e) {
            return Either.left(e.toString());
        }
    }

    public Maybe<String> deregisterTaskDefinition(Integer revision) {
        try {
            ecsClient.deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
            return Option.none();
        } catch (Exception e) {
            return Option.option(e.toString());
        }
    }

}
