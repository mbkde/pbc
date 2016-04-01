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
import com.atlassian.bamboo.executor.NamedExecutors;
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
import com.atlassian.event.api.EventPublisher;
import com.google.common.base.Joiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * this class is an event listener because preBuildQueuedAction requires restart
 * of bamboo when re-deployed.
 */
public class PreBuildQueuedEventListener implements DisposableBean {

    private final IsolatedAgentService isolatedAgentService;
    private final Logger LOG = LoggerFactory.getLogger(PreBuildQueuedEventListener.class);
    private final ErrorUpdateHandler errorUpdateHandler;
    private final BuildQueueManager buildQueueManager;
    private final EventPublisher eventPublisher;
    private final ScheduledExecutorService executor = NamedExecutors.newScheduledThreadPool(1, "Docker Agent Retry Pool");
    private static final int MAX_RETRY_COUNT = 10;


    public PreBuildQueuedEventListener(IsolatedAgentService isolatedAgentService,
                                       ErrorUpdateHandler errorUpdateHandler,
                                       BuildQueueManager buildQueueManager,
                                       EventPublisher eventPublisher) {
        this.isolatedAgentService = isolatedAgentService;
        this.errorUpdateHandler = errorUpdateHandler;
        this.buildQueueManager = buildQueueManager;
        this.eventPublisher = eventPublisher;
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
                if (reschedule(new RetryAgentStartupEvent(event.getDockerImage(), event.getContext(), event.getRetryCount() + 1))) {
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

    private boolean reschedule(RetryAgentStartupEvent event) {
        if (event.getRetryCount() > MAX_RETRY_COUNT) {
            return false;
        }
        //total retry times:
        int X = 10;
        //for retry count 10 and X=10: 10 + 20 + 30 + 40 + 50 + 60 + 70 + 80 + 90 + 100 = 550s
        //for retry count 10 and X=5 : 5 + 10 + 15 + 20 + 25 + 30 + 35 + 40 + 45 + 50 = 225s
        LOG.info("Rescheduling {} for the {} time", event.getContext().getBuildResultKey(), event.getRetryCount());
        executor.schedule(() -> {
            eventPublisher.publish(event);
        }, X * event.getRetryCount(), TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void destroy() throws Exception {
        //TODO this is likely called on reinstall of plugin. Is there a way to have these salvaged
        // and re-inserted into the queue?
        //otherwise we might end up with some unfortunate builds hanging forever.
        executor.shutdownNow();
    }

    private boolean isStillQueued(BuildContext context) {
        LifeCycleState state = context.getBuildResult().getLifeCycleState();
        return LifeCycleState.isPending(state) || LifeCycleState.isQueued(state);
    }
}
