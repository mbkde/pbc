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
package com.atlassian.buildeng.ecs;

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.security.ImpersonationHelper;
import com.atlassian.bamboo.utils.BambooRunnables;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.scheduling.ArnStoppedState;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteFailEvent;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.base.Function;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mkleint
 */
//this class is very similar to RemoteWatchDogJob
//we currently don't have a place to put the shared code between these 2 plugins
//but we eventually should, effectively we need another project/jar
public class ECSWatchdogJob implements PluginJob {
    private final static Logger logger = LoggerFactory.getLogger(ECSWatchdogJob.class);
    private static final int MISSING_TASK_GRACE_PERIOD_MINUTES = 5;
    private static final int CACHE_CLEANUP_TIMEOUT_MINUTES = 30;
    private static final String KEY_MISSING_ARNS_MAP = "MISSING_ARNS_MAP";

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) {
            logger.error("Throwable catched and swallowed to preserve rescheduling of the task", t);
        }
    }

    private void executeImpl(Map<String, Object> jobDataMap) {
        BuildQueueManager buildQueueManager = (BuildQueueManager) ContainerManager.getComponent("buildQueueManager");
        if (buildQueueManager == null) {
            logger.error("no BuildQueueManager");
            throw new IllegalStateException();
        }
        DeploymentExecutionService deploymentExecutionService = (DeploymentExecutionService) ContainerManager.getComponent("deploymentExecutionService");
        if (deploymentExecutionService == null) {
            logger.error("no deploymentExecutionService");
            throw new IllegalStateException();
        }
        DeploymentResultService deploymentResultService = (DeploymentResultService) ContainerManager.getComponent("deploymentResultService");
        if (deploymentResultService == null) {
            logger.error("no deploymentResultService");
            throw new IllegalStateException();
        }
        ErrorUpdateHandler errorUpdateHandler = (ErrorUpdateHandler) ContainerManager.getComponent("errorUpdateHandler");
        if (errorUpdateHandler == null) {
            logger.error("no ErrorUpdateHandler");
            throw new IllegalStateException();
        }
        GlobalConfiguration globalConfig = (GlobalConfiguration) jobDataMap.get("globalConfiguration");
        if (globalConfig == null) {
            logger.error("no GlobalConfiguration");
            throw new IllegalStateException();
        }
        SchedulerBackend backend = (SchedulerBackend) jobDataMap.get("schedulerBackend");
        if (backend == null) {
            logger.error("no SchedulerBackend");
            throw new IllegalStateException();
        }
        EventPublisher eventPublisher = (EventPublisher)ContainerManager.getComponent("eventPublisher");
        if (eventPublisher == null) {
            logger.error("no SchedulerBackend");
            throw new IllegalStateException();
        }
        
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, new Function<BuildQueueManager.QueueItemView<CommonContext>, BuildQueueManager.QueueItemView<CommonContext>>() {
            @Override
            public BuildQueueManager.QueueItemView<CommonContext> apply(BuildQueueManager.QueueItemView<CommonContext> input) {
                return input;
            }
        });
        List<String> arns = new ArrayList<>();
        queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            Map<String, String> bd = t.getView().getCurrentResult().getCustomBuildData();
            String taskArn = bd.get(Constants.RESULT_PREFIX + Constants.RESULT_PART_TASKARN);
            if (taskArn != null) {
                arns.add(taskArn);
            }
        });
        Map<String, Date> missingTaskArns = getMissingTasksArn(jobDataMap);
        if (arns.isEmpty()) {
            return;
        }
        logger.debug("Currently queued docker agent requests {}", arns.size());
        try {
            Collection<ArnStoppedState> tasks = backend.checkStoppedTasks(globalConfig.getCurrentCluster(), arns);
            final Map<String, ArnStoppedState> stoppedTasksByArn = new HashMap<>();
            tasks.stream().forEach((ArnStoppedState t) -> {
                stoppedTasksByArn.put(t.getArn(), t);
            });
            if (!stoppedTasksByArn.isEmpty()) {
                logger.info("Found stopped tasks: {}", stoppedTasksByArn.size());
                logger.debug("Found stopped tasks for {}", stoppedTasksByArn);
                //intentionally not reusing the last time's list, it could have changed since we last looked at it.
                queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
                    CurrentResult current = t.getView().getCurrentResult();
                    String taskArn = current.getCustomBuildData().get(Constants.RESULT_PREFIX + Constants.RESULT_PART_TASKARN);
                    if (taskArn != null) {
                        ArnStoppedState tsk = stoppedTasksByArn.get(taskArn);
                        if (tsk != null) {
                            String error = tsk.getReason();
                            //can be missing both right after being run or when long overdue and wiped from ecs history
                            if ("MISSING".equals(error)) {
                                //if there was a way of finding out when the bambo job was started, we could
                                //have a simple grace period, we have to come up with our own somehow.
                                Date firstTimeMissing = missingTaskArns.get(taskArn);
                                if (firstTimeMissing == null || Duration.ofMillis(System.currentTimeMillis() - firstTimeMissing.getTime()).toMinutes() < MISSING_TASK_GRACE_PERIOD_MINUTES) {
                                    if (firstTimeMissing == null) {
                                        missingTaskArns.put(taskArn, new Date());
                                    }
                                    logger.debug("Task {} missing, still in grace period, not stopping the build.", taskArn);
                                    return; //do not stop or retry, we could be just too fast on checking
                                } 
                            }
                            if (error.contains("CannotCreateContainerError") || error.contains("HostConfigError")) {
                                logger.info("Retrying job {} because of ecs task {} failure: {}", t.getView().getResultKey(), tsk, error);
                                Configuration config = AccessConfiguration.forContext(t.getView());
                                eventPublisher.publish(new RetryAgentStartupEvent(config, t.getView()));
                            } else {
                                logger.info("Stopping job {} because of ecs task {} failure: {}", t.getView().getResultKey(), tsk, error);
                                errorUpdateHandler.recordError(t.getView().getEntityKey(), "Build was not queued due to error:" + error);
                                current.getCustomBuildData().put(Constants.RESULT_ERROR, error);
                                eventPublisher.publish(new DockerAgentRemoteFailEvent(error, t.getView().getEntityKey(), tsk.getArn(), tsk.getContainerArn()));
                                if (t.getView() instanceof BuildContext) {
                                    current.setLifeCycleState(LifeCycleState.NOT_BUILT);
                                    buildQueueManager.removeBuildFromQueue(t.getView().getResultKey());
                                } else if (t.getView() instanceof DeploymentContext) {
                                    DeploymentContext dc = (DeploymentContext) t.getView();
                                    ImpersonationHelper.runWithSystemAuthority((BambooRunnables.NotThrowing) () -> {
                                        //without runWithSystemAuthority() this call terminates execution with a log entry only
                                        DeploymentResult deploymentResult = deploymentResultService.getDeploymentResult(dc.getDeploymentResultId());
                                        if (deploymentResult != null) {
                                            deploymentExecutionService.stop(deploymentResult, null);
                                        }
                                    });
                                } else {
                                    logger.error("unknown type of CommonContext {}", t.getView().getClass());
                                }
                            }
                        }
                    }
                });
            }
        } catch (ECSException ex) {
            logger.error("Error contacting ECS", ex);
        }
    }

    private Map<String, Date> getMissingTasksArn(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        Map<String, Date> map = (Map<String, Date>) data.get(KEY_MISSING_ARNS_MAP);
        if (map == null) {
            map = new HashMap<>();
            data.put(KEY_MISSING_ARNS_MAP, map);
        }
        // trim values that are too old to have
        for (Iterator<Map.Entry<String, Date>> iterator = map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, Date> next = iterator.next();
            if (Duration.ofMillis(System.currentTimeMillis() - next.getValue().getTime()).toMinutes() > CACHE_CLEANUP_TIMEOUT_MINUTES) {
                iterator.remove();
            }
        }
        return map;
    }
}
