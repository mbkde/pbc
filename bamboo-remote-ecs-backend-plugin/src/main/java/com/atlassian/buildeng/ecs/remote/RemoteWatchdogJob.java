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
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import static com.atlassian.buildeng.ecs.remote.ECSIsolatedAgentServiceImpl.createClient;
import com.atlassian.buildeng.ecs.remote.rest.ArnStoppedState;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.base.Function;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteWatchdogJob implements PluginJob {
    String RESULT_PART_TASKARN = "TaskARN";
    String RESULT_PREFIX = "result.isolated.docker.";
    String RESULT_ERROR = "custom.isolated.docker.error";


    private final static Logger logger = LoggerFactory.getLogger(RemoteWatchdogJob.class);

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        BuildQueueManager buildQueueManager = (BuildQueueManager) ContainerManager.getComponent("buildQueueManager");
        if (buildQueueManager == null) {
            logger.info("no BuildQueueManager");
            throw new IllegalStateException();
        }
        DeploymentExecutionService deploymentExecutionService = (DeploymentExecutionService) ContainerManager.getComponent("deploymentExecutionService");
        if (deploymentExecutionService == null) {
            logger.info("no deploymentExecutionService");
            throw new IllegalStateException();
        }
        ErrorUpdateHandler errorUpdateHandler = (ErrorUpdateHandler) ContainerManager.getComponent("errorUpdateHandler");
        if (errorUpdateHandler == null) {
            logger.info("no ErrorUpdateHandler");
            throw new IllegalStateException();
        }
        GlobalConfiguration globalConfig = (GlobalConfiguration) jobDataMap.get("globalConfiguration");
        if (globalConfig == null) {
            logger.info("no GlobalConfiguration");
            throw new IllegalStateException();
        }
        EventPublisher eventPublisher = (EventPublisher)ContainerManager.getComponent("eventPublisher");
        if (eventPublisher == null) {
            logger.info("no SchedulerBackend");
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

        try
        {
            List<ArnStoppedState> result =
                    resource
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .method("GET", new GenericType<List<ArnStoppedState>>(){}, arns);
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
                                //TODO not sure how to deal with queued deployments
//                                DeploymentContext dc = (DeploymentContext) t.getView();
//                                deploymentExecutionService.stop(dc.getDeploymentResultId());
                            } else {
                                logger.error("unknown type of CommonContext {}", t.getView().getClass());
                            }
                        }
                    }
                });
            }

        }
        catch (UniformInterfaceException e)
        {
            int code = e.getResponse().getClientResponseStatus().getStatusCode();
            if (e.getResponse().hasEntity())
            {
                String s = e.getResponse().getEntity(String.class);
            }
            logger.error("Error contacting ECS", e);
        }

    }
}
