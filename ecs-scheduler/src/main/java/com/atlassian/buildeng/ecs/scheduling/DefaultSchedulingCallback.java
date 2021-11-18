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

package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.InstancesSmallerThanAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSchedulingCallback implements SchedulingCallback {
    private final static Logger logger = LoggerFactory.getLogger(DefaultSchedulingCallback.class);

    private final IsolatedDockerRequestCallback callback;
    private final String resultId;

    public DefaultSchedulingCallback(IsolatedDockerRequestCallback callback, String resultId) {
        this.callback = callback;
        this.resultId = resultId;
    }

    @Override
    public void handle(SchedulingResult schedulingResult) {
        IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
        StartTaskResult startTaskResult = schedulingResult.getStartTaskResult();
        startTaskResult.getTasks().stream().findFirst().ifPresent((com.amazonaws.services.ecs.model.Task t) -> {
            toRet.withCustomResultData(Constants.RESULT_PART_TASKARN, t.getTaskArn());
            toRet.withCustomResultData(Constants.RESULT_PART_EC2_INSTANCEID, schedulingResult.getEc2InstanceId());
            toRet.withCustomResultData(Constants.RESULT_PART_ECS_CONTAINERARN, schedulingResult.getContainerArn());
        });
        logger.info("ECS Returned: {}", startTaskResult);
        List<Failure> failures = startTaskResult.getFailures();
        if (failures.size() == 1) {
            String err = failures.get(0).getReason();
            if (err.startsWith("RESOURCE")) {
                logger.info("ECS cluster is overloaded, waiting for auto-scaling and retrying");
                toRet.withRetryRecoverable("Not enough resources available now.");
            } else if ("AGENT".equals(err)) {
                logger.info("We've scheduled on AGENT disabled instance, should be just flaky AWS. Retrying.");
                toRet.withRetryRecoverable("AGENT - The container instance that you attempted to launch a task onto has an agent which is currently disconnected.");
            } else {
                toRet.withError(mapRunTaskErrorToDescription(err));
            }
        } else {
            for (Failure err : startTaskResult.getFailures()) {
                toRet.withError(mapRunTaskErrorToDescription(err.getReason()));
            }
        }
        callback.handle(toRet);
    }

    @Override
    public void handle(ECSException exception) {
        IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
        logger.warn("Failed to schedule {}, treating as overload: {}", resultId, exception);
        if (exception.getCause() instanceof TimeoutException) {
            toRet.withRetryRecoverable("Request timed out without completing.");
        } else if (exception.getCause() instanceof InstancesSmallerThanAgentException) {
            toRet.withError(exception.getMessage());
        } else {
            toRet.withRetryRecoverable("No Container Instance currently available. Reason: " + exception.getLocalizedMessage());
        }
        callback.handle(toRet);
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
