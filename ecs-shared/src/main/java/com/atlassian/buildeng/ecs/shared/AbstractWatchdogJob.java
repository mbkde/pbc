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
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteFailEvent;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteSilentRetryEvent;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import static com.google.common.base.Preconditions.checkNotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//this class is very similar to ECSWatchDogJob
//we currently don't have a place to put the shared code between these 2 plugins
//but we eventually should, effectively we need another project/jar
public abstract class AbstractWatchdogJob implements PluginJob {
    private static final String RESULT_PART_TASKARN = "TaskARN";
    private static final String RESULT_PREFIX = "result.isolated.docker.";
    private static final String RESULT_ERROR = "custom.isolated.docker.error";
    private static final int MISSING_TASK_GRACE_PERIOD_MINUTES = 5;
    private static final int CACHE_CLEANUP_TIMEOUT_MINUTES = 30;
    private static final String KEY_MISSING_ARNS_MAP = "MISSING_ARNS_MAP";


    private final static Logger logger = LoggerFactory.getLogger(AbstractWatchdogJob.class);

    @Override
    public final void execute(Map<String, Object> jobDataMap) {
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) {
            logger.error("Throwable catched and swallowed to preserve rescheduling of the task", t);
        }
    }

    protected abstract List<StoppedState> retrieveStoppedTasksByArn(List<String> arns, Map<String, Object> jobDataMap) throws Exception;


    private void executeImpl(Map<String, Object> jobDataMap) throws Exception {
        BuildQueueManager buildQueueManager = getService(BuildQueueManager.class, "buildQueueManager");
        DeploymentExecutionService deploymentExecutionService = getService(DeploymentExecutionService.class, "deploymentExecutionService");
        DeploymentResultService deploymentResultService = getService(DeploymentResultService.class, "deploymentResultService");
        ErrorUpdateHandler errorUpdateHandler = getService(ErrorUpdateHandler.class, "errorUpdateHandler");
        EventPublisher eventPublisher = getService(EventPublisher.class, "eventPublisher");
        
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, new com.google.common.base.Function<BuildQueueManager.QueueItemView<CommonContext>, BuildQueueManager.QueueItemView<CommonContext>>() {
            @Override
            public BuildQueueManager.QueueItemView<CommonContext> apply(BuildQueueManager.QueueItemView<CommonContext> input) {
                return input;
            }
        });
        List<String> arns = getQueuedARNs(queue);
        Map<String, Date> missingTaskArns = getMissingTasksArn(jobDataMap);
        if (arns.isEmpty()) {
            return;
        }
        logger.debug("Currently queued docker agent requests {}", arns.size());

        Map<String, StoppedState> stoppedTasksByArn =
                retrieveStoppedTasksByArn(arns, jobDataMap).stream().collect(Collectors.toMap(StoppedState::getArn,
                                          Function.identity()));

        if (!stoppedTasksByArn.isEmpty()) {
            logger.info("Found stopped tasks: {}", stoppedTasksByArn.size());
            logger.debug("Found stopped tasks for {}", stoppedTasksByArn);
            //intentionally not reusing the last time's list, it could have changed since we last looked at it.
            queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
                CurrentResult current = t.getView().getCurrentResult();
                String taskArn = current.getCustomBuildData().get(RESULT_PREFIX + RESULT_PART_TASKARN);
                if (taskArn != null) {
                    StoppedState tsk = stoppedTasksByArn.get(taskArn);
                    if (tsk != null) {
                        String error = tsk.getReason();
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

                        if (error.contains("CannotStartContainerError") 
                                || error.contains("CannotCreateContainerError")
                                || error.contains("HostConfigError")) {
                            logger.info("Retrying job {} because of ecs task {} failure: {}", t.getView().getResultKey(), tsk, error);
                            Configuration config = AccessConfiguration.forContext(t.getView());
                            eventPublisher.publish(new RetryAgentStartupEvent(config, t.getView()));
                            //monitoring only
                            eventPublisher.publish(new DockerAgentRemoteSilentRetryEvent(error, t.getView().getEntityKey(), tsk.getArn(), tsk.getContainerArn()));
                        } else {       
                            logger.info("Stopping job {} because of ecs task {} failure: {}", t.getView().getResultKey(), tsk, error);
                            errorUpdateHandler.recordError(t.getView().getEntityKey(), "Build was not queued due to error:" + error);
                            current.getCustomBuildData().put(RESULT_ERROR, error);
                            //monitoring only
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

    }

    private List<String> getQueuedARNs(QueueManagerView<CommonContext, CommonContext> queue) {
        List<String> arns = new ArrayList<>();
        queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            Map<String, String> bd = t.getView().getCurrentResult().getCustomBuildData();
            String taskArn = bd.get(RESULT_PREFIX + RESULT_PART_TASKARN);
            if (taskArn != null) {
                arns.add(taskArn);
            }
        });
        return arns;
    }

    protected final <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
        final Object obj = checkNotNull(jobDataMap.get(serviceKey), "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }

    protected final <T> T getService(Class<T> type, String serviceKey) {
        final Object obj = checkNotNull(ContainerManager.getComponent(serviceKey), "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
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
