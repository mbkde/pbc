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
import com.atlassian.buildeng.isolated.docker.events.RetryAgentStartupEvent;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
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
    private final AgentCreationRescheduler rescheduler;


    public PreBuildQueuedEventListener(IsolatedAgentService isolatedAgentService,
                                       ErrorUpdateHandler errorUpdateHandler,
                                       BuildQueueManager buildQueueManager,
                                       AgentCreationRescheduler rescheduler) {
        this.isolatedAgentService = isolatedAgentService;
        this.errorUpdateHandler = errorUpdateHandler;
        this.buildQueueManager = buildQueueManager;
        this.rescheduler = rescheduler;
    }

    @EventListener
    public void call(BuildQueuedEvent event) {
        BuildContext buildContext = event.getContext();
        Configuration config = Configuration.forBuildContext(buildContext);
        buildContext.getBuildResult().getCustomBuildData().put(Constants.ENABLED_FOR_JOB, "" + config.isEnabled());
        if (config.isEnabled()) {
            retry(new RetryAgentStartupEvent(config.getDockerImage(), buildContext, 0));
        }
    }

    @EventListener
    public void retry(RetryAgentStartupEvent event) {
        //when we arrive here, user could have cancelled the build.
        if (!isStillQueued(event.getContext())) {
            return;
        }
        boolean terminateBuild = false;
        try {
            IsolatedDockerAgentResult result = isolatedAgentService.startAgent(
                    new IsolatedDockerAgentRequest(event.getDockerImage(), event.getContext().getBuildResultKey()));
            //custom items pushed by the implementation, we give it a unique prefix
            result.getCustomResultData().entrySet().stream().forEach((ent) -> {
                event.getContext().getBuildResult().getCustomBuildData().put(Constants.RESULT_PREFIX + ent.getKey(), ent.getValue());
            });
            if (result.isRetryRecoverable()) {
                if (rescheduler.reschedule(new RetryAgentStartupEvent(event.getDockerImage(), event.getContext(), event.getRetryCount() + 1))) {
                    return;
                }
            }
            if (result.hasErrors()) {
                terminateBuild = true;
                errorUpdateHandler.recordError(event.getContext().getEntityKey(), "Build was not queued due to error:" + Joiner.on("\n").join(result.getErrors()));
                event.getContext().getBuildResult().getCustomBuildData().put(Constants.RESULT_ERROR, Joiner.on("\n").join(result.getErrors()));
            }
        } catch (IsolatedDockerAgentException ex) {
            terminateBuild = true;
            errorUpdateHandler.recordError(event.getContext().getEntityKey(), "Build was not queued due to error", ex);
            event.getContext().getBuildResult().getCustomBuildData().put(Constants.RESULT_ERROR, ex.getLocalizedMessage());
        }
        if (terminateBuild) {
            event.getContext().getBuildResult().setLifeCycleState(LifeCycleState.NOT_BUILT);
            buildQueueManager.removeBuildFromQueue(event.getContext().getPlanResultKey());
        }

    }

    private boolean isStillQueued(BuildContext context) {
        LifeCycleState state = context.getBuildResult().getLifeCycleState();
        return LifeCycleState.isPending(state) || LifeCycleState.isQueued(state);
    }
}
