/*
 * Copyright 2016 Atlassian.
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

package com.atlassian.buildeng.ecs.remote;

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
import static com.atlassian.buildeng.ecs.remote.ECSIsolatedAgentServiceImpl.createClient;
import com.atlassian.buildeng.ecs.remote.rest.ArnStoppedState;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteWatchdogJob implements PluginJob {
    String RESULT_PART_TASKARN = "TaskARN";
    String RESULT_PREFIX = "result.isolated.docker.";
    String RESULT_ERROR = "custom.isolated.docker.error";


    private final static Logger logger = LoggerFactory.getLogger(RemoteWatchdogJob.class);

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) {
            logger.error("Throwable catched and swallowed to preserve rescheduling of the task", t);
        }
    }

    private void executeImpl(Map<String, Object> jobDataMap) {
        BuildQueueManager buildQueueManager = getService(BuildQueueManager.class, "buildQueueManager");
        DeploymentExecutionService deploymentExecutionService = getService(DeploymentExecutionService.class, "deploymentExecutionService");
        DeploymentResultService deploymentResultService = getService(DeploymentResultService.class, "deploymentResultService");
        ErrorUpdateHandler errorUpdateHandler = getService(ErrorUpdateHandler.class, "errorUpdateHandler");
        GlobalConfiguration globalConfig = getService(GlobalConfiguration.class, "globalConfiguration", jobDataMap);
        EventPublisher eventPublisher = getService(EventPublisher.class, "eventPublisher");
        
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, new Function<BuildQueueManager.QueueItemView<CommonContext>, BuildQueueManager.QueueItemView<CommonContext>>() {
            @Override
            public BuildQueueManager.QueueItemView<CommonContext> apply(BuildQueueManager.QueueItemView<CommonContext> input) {
                return input;
            }
        });
        List<String> arns = new ArrayList<>();
        queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            Map<String, String> bd = t.getView().getCurrentResult().getCustomBuildData();
            String taskArn = bd.get(RESULT_PREFIX + RESULT_PART_TASKARN);
            if (taskArn != null) {
                arns.add(taskArn);
            }
        });
        if (arns.isEmpty()) {
            return;
        }
        logger.debug("Currently queued docker agent requests {}", arns.size());
        Client client = createClient();

        final WebResource resource = client.resource(globalConfig.getCurrentServer() + "/rest/scheduler/stopped");
//        resource.addFilter(new HTTPBasicAuthFilter(username, password));

        try {
            List<ArnStoppedState> result =
                    resource
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .post(new GenericType<List<ArnStoppedState>>(){}, arns);
            final Map<String, ArnStoppedState> stoppedTasksByArn = new HashMap<>();
            result.stream().forEach((ArnStoppedState t) -> {
                stoppedTasksByArn.put(t.getArn(), t);
            });

            if (!stoppedTasksByArn.isEmpty()) {
                logger.info("Found stopped tasks: {}", stoppedTasksByArn.size());
                logger.debug("Found stopped tasks for {}", stoppedTasksByArn);
                //intentionally not reusing the last time's list, it could have changed since we last looked at it.
                queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
                    CurrentResult current = t.getView().getCurrentResult();
                    String taskArn = current.getCustomBuildData().get(RESULT_PREFIX + RESULT_PART_TASKARN);
                    if (taskArn != null) {
                        ArnStoppedState tsk = stoppedTasksByArn.get(taskArn);
                        if (tsk != null) {
                            String error = tsk.getReason();
                            logger.info("Stopping job {} because of ecs task {} failure: {}", t.getView().getResultKey(), tsk, error);
                            errorUpdateHandler.recordError(t.getView().getEntityKey(), "Build was not queued due to error:" + error);
                            current.getCustomBuildData().put(RESULT_ERROR, error);
//TODO                                eventPublisher.publish(new DockerAgentRemoteFailEvent(error, t.getView().getEntityKey()));
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
                });
            }

        }
        catch (UniformInterfaceException e) {
            int code = e.getResponse().getClientResponseStatus().getStatusCode();
            String s = "";
            if (e.getResponse().hasEntity()) {
                s = e.getResponse().getEntity(String.class);
            }
            logger.error("Error contacting ECS:" + code + " " + s, e);
        }
    }

    private <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
        final Object obj = checkNotNull(jobDataMap.get(serviceKey), "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }

    private <T> T getService(Class<T> type, String serviceKey) {
        final Object obj = checkNotNull(ContainerManager.getComponent(serviceKey), "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }

}
