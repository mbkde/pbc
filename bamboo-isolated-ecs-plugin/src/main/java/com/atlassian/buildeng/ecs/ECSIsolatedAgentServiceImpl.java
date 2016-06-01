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

import com.amazonaws.services.ecs.model.Failure;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageNotRegisteredException;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.SchedulingCallback;
import com.atlassian.buildeng.ecs.scheduling.SchedulingRequest;
import com.atlassian.buildeng.ecs.scheduling.SchedulingResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);

    private final GlobalConfiguration globalConfiguration;
    private final ECSScheduler ecsScheduler;
    private final PluginScheduler pluginScheduler;
    private final SchedulerBackend schedulerBackend;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration, ECSScheduler ecsScheduler, 
            PluginScheduler pluginScheduler, SchedulerBackend schedulerBackend) {
        this.globalConfiguration = globalConfiguration;
        this.ecsScheduler = ecsScheduler;
        this.pluginScheduler = pluginScheduler;
        this.schedulerBackend = schedulerBackend;
    }

    // Isolated Agent Service methods
    @Override
    public void startAgent(IsolatedDockerAgentRequest req, IsolatedDockerRequestCallback callback) {
        Integer revision = globalConfiguration.getAllRegistrations().get(req.getDockerImage());
        String resultId = req.getResultKey();
        if (revision == null) {
            callback.handle(new ImageNotRegisteredException(req.getDockerImage()));
            return;
        }
        logger.info("Spinning up new docker agent from task definition {}:{} {}", Constants.TASK_DEFINITION_NAME, revision, resultId);
        SchedulingRequest schedulingRequest = new SchedulingRequest(
                req.getUniqueIdentifier(),
                resultId,
                revision,
                Constants.TASK_CPU,
                Constants.TASK_MEMORY);
        ecsScheduler.schedule(schedulingRequest,
                new SchedulingCallback() {
                    @Override
                    public void handle(SchedulingResult schedulingResult) {
                        IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
                        StartTaskResult startTaskResult = schedulingResult.getStartTaskResult();
                        startTaskResult.getTasks().stream().findFirst().ifPresent(t -> {
                            toRet.withCustomResultData(Constants.RESULT_PART_TASKARN, t.getTaskArn());
                        });
                        logger.info("ECS Returned: {}", startTaskResult);
                        List<Failure> failures = startTaskResult.getFailures();
                        if (failures.size() == 1) {
                            String err = failures.get(0).getReason();
                            if (err.startsWith("RESOURCE")) {
                                logger.info("ECS cluster is overloaded, waiting for auto-scaling and retrying");
                                toRet.withRetryRecoverable("Not enough resources available now.");
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
                        } else {
                            toRet.withRetryRecoverable("No Container Instance currently available");
                        }
                        callback.handle(toRet);
                    }
        });
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

    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        config.put("globalConfiguration", globalConfiguration);
        config.put("schedulerBackend", schedulerBackend);
        pluginScheduler.scheduleJob(Constants.PLUGIN_JOB_KEY, ECSWatchdogJob.class, config, new Date(), Constants.PLUGIN_JOB_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(Constants.PLUGIN_JOB_KEY);
    }

}
