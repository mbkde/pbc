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

package com.atlassian.buildeng.ecs.shared;

import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteFailEvent;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteSilentRetryEvent;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.buildeng.spi.isolated.docker.WatchdogJob;
import com.atlassian.event.api.EventPublisher;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// this class is very similar to ECSWatchDogJob
// we currently don't have a place to put the shared code between these 2 plugins
// but we eventually should, effectively we need another project/jar
public abstract class AbstractWatchdogJob extends WatchdogJob {
    private static final String RESULT_PART_TASKARN = "TaskARN";
    private static final String RESULT_PREFIX = "result.isolated.docker.";
    private static final String RESULT_ERROR = "custom.isolated.docker.error";
    private static final int MISSING_TASK_GRACE_PERIOD_MINUTES = 5;
    private static final int CACHE_CLEANUP_TIMEOUT_MINUTES = 30;
    private static final String KEY_MISSING_ARNS_MAP = "MISSING_ARNS_MAP";


    private static final Logger logger = LoggerFactory.getLogger(AbstractWatchdogJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            executeImpl(context.getJobDetail().getJobDataMap());
        } catch (Throwable t) {
            // this is throwable because of NoClassDefFoundError and alike.
            // These are not Exception subclasses and actually
            // thowing something here will stop rescheduling the job forever (until next redeploy)
            logger.error("Exception catched and swallowed to preserve rescheduling of the task", t);
        }
    }

    protected abstract List<StoppedState> retrieveStoppedTasksByArn(List<String> arns, Map<String, Object> jobDataMap)
            throws Exception;


    private void executeImpl(Map<String, Object> jobDataMap) throws Exception {
        BuildQueueManager buildQueueManager = getService(BuildQueueManager.class, "buildQueueManager");
        DeploymentExecutionService deploymentExecutionService =
                getService(DeploymentExecutionService.class, "deploymentExecutionService");
        DeploymentResultService deploymentResultService =
                getService(DeploymentResultService.class, "deploymentResultService");
        ErrorUpdateHandler errorUpdateHandler = getService(ErrorUpdateHandler.class, "errorUpdateHandler");
        EventPublisher eventPublisher = getService(EventPublisher.class, "eventPublisher");
        IsolatedAgentService isolatedAgentService =
                getService(IsolatedAgentService.class, "isolatedAgentService", jobDataMap);

        List<String> arns = getQueuedARNs(buildQueueManager);
        Map<String, Date> missingTaskArns = getMissingTasksArn(jobDataMap);
        if (arns.isEmpty()) {
            return;
        }
        logger.debug("Currently queued docker agent requests {}", arns.size());

        Map<String, StoppedState> stoppedTasksByArn = retrieveStoppedTasksByArn(arns, jobDataMap)
                .stream()
                .collect(Collectors.toMap(StoppedState::getArn, Function.identity()));

        if (!stoppedTasksByArn.isEmpty()) {
            logger.info("Found stopped tasks: {}", stoppedTasksByArn.size());
            logger.debug("Found stopped tasks for {}", stoppedTasksByArn);
            // intentionally not reusing the last time's list, it could have changed since we last looked at it.
            DockerAgentBuildQueue.currentlyQueued(buildQueueManager).forEach((CommonContext t) -> {
                CurrentResult current = t.getCurrentResult();
                String taskArn = current.getCustomBuildData().get(RESULT_PREFIX + RESULT_PART_TASKARN);
                if (taskArn != null) {
                    StoppedState tsk = stoppedTasksByArn.get(taskArn);
                    if (tsk != null) {
                        String error = tsk.getReason();
                        if ("MISSING".equals(error)) {
                            // if there was a way of finding out when the bambo job was started, we could
                            // have a simple grace period, we have to come up with our own somehow.
                            Date firstTimeMissing = missingTaskArns.get(taskArn);
                            if (firstTimeMissing == null ||
                                    Duration
                                            .ofMillis(System.currentTimeMillis() - firstTimeMissing.getTime())
                                            .toMinutes() < MISSING_TASK_GRACE_PERIOD_MINUTES) {
                                if (firstTimeMissing == null) {
                                    missingTaskArns.put(taskArn, new Date());
                                }
                                logger.debug("Task {} missing, still in grace period, not stopping the build.",
                                        taskArn);
                                return; // do not stop or retry, we could be just too fast on checking
                            }
                        }

                        if (error.contains("CannotStartContainerError") ||
                                error.contains("CannotCreateContainerError") ||
                                error.contains("HostConfigError")) {
                            logger.info("Retrying job {} because of ecs task {} failure: {}",
                                    t.getResultKey(),
                                    tsk,
                                    error);
                            Configuration config = AccessConfiguration.forContext(t);
                            eventPublisher.publish(new RetryAgentStartupEvent(config, t));
                            // monitoring only
                            eventPublisher.publish(new DockerAgentRemoteSilentRetryEvent(error,
                                    t.getEntityKey(),
                                    tsk.getArn(),
                                    tsk.getContainerArn()));
                        } else {
                            logger.info("Stopping job {} because of ecs task {} failure: {}",
                                    t.getResultKey(),
                                    tsk,
                                    error);
                            errorUpdateHandler.recordError(t.getEntityKey(),
                                    "Build was not queued due to error:" + error);
                            current.getCustomBuildData().put(RESULT_ERROR, error);
                            generateRemoteFailEvent(t, error, tsk, isolatedAgentService, eventPublisher);
                            killBuild(deploymentExecutionService,
                                    deploymentResultService,
                                    logger,
                                    buildQueueManager,
                                    t,
                                    current);
                        }
                    }
                }
            });
        }

    }

    private void generateRemoteFailEvent(CommonContext t,
            String error,
            StoppedState tsk,
            IsolatedAgentService isolatedAgentService,
            EventPublisher eventPublisher) {
        Configuration c = AccessConfiguration.forContext(t);
        Map<String, String> customData = new HashMap<>(t.getCurrentResult().getCustomBuildData());
        // sort of implementation detail, would be nice to place elsewhere but it's shared across
        // ecs-shared and bamboo-isolated-docker modules
        customData
                .entrySet()
                .removeIf((Map.Entry<String, String> tt) -> !tt.getKey().startsWith("result.isolated.docker."));
        Map<String, URL> containerLogs = isolatedAgentService.getContainerLogs(c, customData);
        DockerAgentRemoteFailEvent event = new DockerAgentRemoteFailEvent(error,
                t.getEntityKey(),
                tsk.getArn(),
                tsk.getContainerArn(),
                containerLogs);
        eventPublisher.publish(event);
    }

    private List<String> getQueuedARNs(BuildQueueManager buildQueueManager) {
        List<String> arns = new ArrayList<>();
        DockerAgentBuildQueue.currentlyQueued(buildQueueManager).forEach((CommonContext t) -> {
            Map<String, String> bd = t.getCurrentResult().getCustomBuildData();
            String taskArn = bd.get(RESULT_PREFIX + RESULT_PART_TASKARN);
            if (taskArn != null) {
                arns.add(taskArn);
            }
        });
        return arns;
    }

    private Map<String, Date> getMissingTasksArn(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        Map<String, Date> map = (Map<String, Date>) data.get(KEY_MISSING_ARNS_MAP);
        if (map == null) {
            map = new HashMap<>();
            data.put(KEY_MISSING_ARNS_MAP, map);
        }
        // trim values that are too old to have
        for (Iterator<Map.Entry<String, Date>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Date> next = iterator.next();
            if (Duration.ofMillis(System.currentTimeMillis() - next.getValue().getTime()).toMinutes() >
                    CACHE_CLEANUP_TIMEOUT_MINUTES) {
                iterator.remove();
            }
        }
        return map;
    }

}
