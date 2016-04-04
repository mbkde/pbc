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
import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.StartTaskRequest;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskOverride;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageNotRegisteredException;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);

    private final GlobalConfiguration globalConfiguration;
    private final ECSScheduler ecsScheduler;

    @Autowired
    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration, ECSScheduler scheduler) {
        this.globalConfiguration = globalConfiguration;
        this.ecsScheduler = scheduler;
    }
 
    private StartTaskRequest createStartTaskRequest(String resultId, Integer revision, @NotNull String containerInstanceArn) throws ECSException {
        ContainerOverride buildResultOverride = new ContainerOverride()
                .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_RESULT_ID).withValue(resultId))
                .withName(Constants.AGENT_CONTAINER_NAME);
        return new StartTaskRequest()
                .withCluster(globalConfiguration.getCurrentCluster())
                .withContainerInstances(containerInstanceArn)
                .withTaskDefinition(Constants.TASK_DEFINITION_NAME + ":" + revision)
                .withOverrides(new TaskOverride().withContainerOverrides(buildResultOverride));
    }


    // Isolated Agent Service methods
    @Override
    public IsolatedDockerAgentResult startAgent(IsolatedDockerAgentRequest req) throws ImageNotRegisteredException, ECSException {
        Integer revision = globalConfiguration.getAllRegistrations().get(req.getDockerImage());
        final IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
        String resultId = req.getBuildResultKey();
        AmazonECSClient ecsClient = new AmazonECSClient();
        if (revision == null) {
            throw new ImageNotRegisteredException(req.getDockerImage());
        }

        logger.info("Spinning up new docker agent from task definition {}:{} {}", Constants.TASK_DEFINITION_NAME, revision, req.getBuildResultKey());
        boolean finished = false;
        while (!finished) {
            try {
                String containerInstanceArn = null;
                try {
                     containerInstanceArn = ecsScheduler.schedule(globalConfiguration.getCurrentCluster(), Constants.TASK_MEMORY, Constants.TASK_CPU);
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
    
    @Override
    public List<String> getKnownDockerImages() {
        List<String> toRet = new ArrayList<>(globalConfiguration.getAllRegistrations().keySet());
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
