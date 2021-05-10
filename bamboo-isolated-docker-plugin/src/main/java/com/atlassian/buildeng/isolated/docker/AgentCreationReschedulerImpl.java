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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.deployments.events.DeploymentQueuedEvent;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.executor.NamedExecutors;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.events.BuildQueuedEvent;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.AgentCreationRescheduler;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentCreationReschedulerImpl implements LifecycleAware, AgentCreationRescheduler  {
    private final Logger logger = LoggerFactory.getLogger(AgentCreationReschedulerImpl.class);
    private final EventPublisher eventPublisher;
    private final BuildQueueManager buildQueueManager;
    private final ScheduledExecutorService executor = NamedExecutors.newScheduledThreadPool(1, 
            "Docker Agent Retry Pool");
    private static final int MAX_RETRY_COUNT = 90;
    private static final long RETRY_DELAY = Constants.RETRY_DELAY.getSeconds(); //20 seconds times 90 = 30 minutes
    private static final String KEY = "custom.isolated.docker.waiting";

    private AgentCreationReschedulerImpl(EventPublisher eventPublisher, BuildQueueManager buildQueueManager) {
        this.eventPublisher = eventPublisher;
        this.buildQueueManager = buildQueueManager;
    }
    
    public boolean reschedule(RetryAgentStartupEvent event) {
        if (event.getRetryCount() > MAX_RETRY_COUNT) {
            return false;
        }
        logger.info("Rescheduling {} for the {} time", event.getContext().getResultKey(), event.getRetryCount());
        event.getContext().getCurrentResult().getCustomBuildData().put(KEY, "true");
        executor.schedule(() -> {
            logger.info("Publishing {} for the {} time", event.getContext().getResultKey(), event.getRetryCount());
            eventPublisher.publish(event);
            event.getContext().getCurrentResult().getCustomBuildData().remove(KEY);
        }, RETRY_DELAY, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void onStart() {
        logger.info("Checking what jobs are queued on plugin restart.");
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager,
                (BuildQueueManager.QueueItemView<CommonContext> input) -> input);
        queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            Map<String, String> bd = t.getView().getCurrentResult().getCustomBuildData();
            Configuration c = AccessConfiguration.forContext(t.getView());
            if (c.isEnabled()) {
                String wasWaiting = bd.get(KEY);
                if (wasWaiting != null) {
                    //we need to restart this guy.
                    logger.info("Restarted scheduling of {} after plugin restart.", t.getView().getResultKey());
                    eventPublisher.publish(new RetryAgentStartupEvent(c, t.getView()));
                } else {
                    String buildKey = bd.get(DockerAgentBuildQueue.BUILD_KEY);
                    // if the build key is not present in CurrentResult customBuildData or does not match our context
                    // build key then PreBuildQueuedEventListener.call(BuildQueuedEvent event) was never called
                    // i.e., the plugin was offline when the build event was fired
                    // else, the docker agent for this one is either coming up online or will be dumped/stopped by
                    // ECSWatchDogJob
                    if (buildKey == null || !buildKey.equals(t.getView().getBuildKey().getKey())) {
                        logger.info("Refire build/deployment event for {} after plugin restart.",
                                t.getView().getResultKey());
                        if (t.getView() instanceof BuildContext) {
                            eventPublisher.publish(new BuildQueuedEvent(buildQueueManager, (BuildContext) t.getView()));
                        } else if (t.getView() instanceof DeploymentContext) {
                            eventPublisher.publish(new DeploymentQueuedEvent((DeploymentContext) t.getView()));
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onStop() {
        logger.info("Destroying executor on plugin stop");
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.debug(ex.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }
    

}
