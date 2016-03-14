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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.events.BuildQueuedEvent;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.event.api.EventListener;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this class is an event listener because preBuildQueuedAction requires restart
 * of bamboo when re-deployed.
 */
public class PreBuildQueuedEventListener {

    private final IsolatedAgentService isolatedAgentService;
    private final Logger LOG = LoggerFactory.getLogger(PreBuildQueuedEventListener.class);
    private final ErrorUpdateHandler errorUpdateHandler;
    private final BuildQueueManager buildQueueManager;


    public PreBuildQueuedEventListener(IsolatedAgentService isolatedAgentService,
                                       ErrorUpdateHandler errorUpdateHandler,
                                       BuildQueueManager buildQueueManager) {
        this.isolatedAgentService = isolatedAgentService;
        this.errorUpdateHandler = errorUpdateHandler;
        this.buildQueueManager = buildQueueManager;
    }

    @EventListener
    public void call(BuildQueuedEvent event) throws InterruptedException, Exception {
        BuildContext buildContext = event.getContext();
        Configuration config = Configuration.forBuildContext(buildContext);
        buildContext.getBuildResult().getCustomBuildData().put(Constants.ENABLED_FOR_JOB, "" + config.isEnabled());
        if (config.isEnabled()) {
            boolean terminate = false;
            try {
                buildContext.getBuildResult().getCustomBuildData().put(Constants.ENABLED_FOR_JOB, "true");
                buildContext.getBuildResult().getCustomBuildData().put(Constants.DOCKER_IMAGE, config.getDockerImage());

                IsolatedDockerAgentResult result = isolatedAgentService.startAgent(
                        new IsolatedDockerAgentRequest(config.getDockerImage(), buildContext.getBuildResultKey()));
                //custom items pushed by the implementation, we give it a unique prefix
                result.getCustomResultData().entrySet().stream().forEach((ent) -> {
                    buildContext.getBuildResult().getCustomBuildData().put(Constants.RESULT_PREFIX + ent.getKey(), ent.getValue());
                });
                if (result.hasErrors()) {
                    terminate = true;
                    errorUpdateHandler.recordError(buildContext.getEntityKey(), "Build was not queued due to error:" + Joiner.on("\n").join(result.getErrors()));
                    buildContext.getBuildResult().getCustomBuildData().put(Constants.RESULT_ERROR, Joiner.on("\n").join(result.getErrors()));
                }
            } catch (Exception ex) {
                terminate = true;
                errorUpdateHandler.recordError(buildContext.getEntityKey(), "Build was not queued due to error", ex);
                buildContext.getBuildResult().getCustomBuildData().put(Constants.RESULT_ERROR, ex.getLocalizedMessage());
            }
            if (terminate) {
                buildContext.getBuildResult().setLifeCycleState(LifeCycleState.NOT_BUILT);
                buildQueueManager.removeBuildFromQueue(buildContext.getPlanResultKey());
            }
        }
    }

}
