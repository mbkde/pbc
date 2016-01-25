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
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.atlassian.bamboo.agent.elastic.server.ElasticAccountBean;
import com.atlassian.bamboo.agent.elastic.server.ElasticConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);
    private final ElasticAccountBean elasticAccountBean;

    @Autowired
    public ECSIsolatedAgentServiceImpl(ElasticAccountBean elasticAccountBean) {
        this.elasticAccountBean = elasticAccountBean;
    }

    @Override
    public void startInstance(IsolatedDockerAgentRequest req) {
        final ElasticConfiguration elasticConfig = elasticAccountBean.getElasticConfig();

        if (elasticConfig != null) {
            AWSCredentials awsCredentials = new BasicAWSCredentials(elasticConfig.getAwsAccessKeyId(),
                                                                    elasticConfig.getAwsSecretKey());
            AmazonECS amazonECS = new AmazonECSClient(awsCredentials);
            logger.info("Spinning up new docker agent");
            RunTaskRequest runTaskRequest = new RunTaskRequest();
            runTaskRequest.setCluster("shipit-300-ttsang");
            runTaskRequest.setTaskDefinition(req.getDockerImage());
            runTaskRequest.setCount(1);
            RunTaskResult runTaskResult = amazonECS.runTask(runTaskRequest);
            logger.info(runTaskResult.toString());
        } else {
            logger.error("No AWS credentials, aborting.");
        }
    }
}
