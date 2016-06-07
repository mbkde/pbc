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
package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.executor.NamedExecutors;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.isolated.docker.events.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mkleint
 */
public class AgentCreationRescheduler implements LifecycleAware  {
    private final Logger LOG = LoggerFactory.getLogger(AgentCreationRescheduler.class);
    private final EventPublisher eventPublisher;
    private final BuildQueueManager buildQueueManager;
    private final ScheduledExecutorService executor = NamedExecutors.newScheduledThreadPool(1, "Docker Agent Retry Pool");
    private static final int MAX_RETRY_COUNT = 20;
    private static final String KEY = "custom.isolated.docker.waiting";

    private AgentCreationRescheduler(EventPublisher eventPublisher, BuildQueueManager buildQueueManager) {
        this.eventPublisher = eventPublisher;
        this.buildQueueManager = buildQueueManager;
    }
    
    public boolean reschedule(RetryAgentStartupEvent event) {
        if (event.getRetryCount() > MAX_RETRY_COUNT) {
            return false;
        }
        //total retry times:
        int X = 5;
        // for retry count 20 and X=5: 5 + 10 + 15 + ... + 100 = 1050s = 17.5 min
        //for retry count 10 and X=10: 10 + 20 + 30 + 40 + 50 + 60 + 70 + 80 + 90 + 100 = 550s
        //for retry count 10 and X=5 : 5 + 10 + 15 + 20 + 25 + 30 + 35 + 40 + 45 + 50 = 225s
        LOG.info("Rescheduling {} for the {} time", event.getContext().getResultKey(), event.getRetryCount());
        event.getContext().getCurrentResult().getCustomBuildData().put(KEY, "true");
        executor.schedule(() -> {
            LOG.info("Publishing {} for the {} time", event.getContext().getResultKey(), event.getRetryCount());
            eventPublisher.publish(event);
            event.getContext().getCurrentResult().getCustomBuildData().remove(KEY);
        }, X * event.getRetryCount(), TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void onStart() {
        LOG.info("Checking what jobs are queued on plugin restart.");
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, (BuildQueueManager.QueueItemView<CommonContext> input) -> input);
        queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            Map<String, String> bd = t.getView().getCurrentResult().getCustomBuildData();
            Configuration c = Configuration.forContext(t.getView());
            if (c.isEnabled()) {
                String wasWaiting = bd.get(KEY);
                if (wasWaiting != null) {
                    //we need to restart this guy.
                    LOG.info("Restarted scheduling of {} after plugin restart.", t.getView().getResultKey());
                    eventPublisher.publish(new RetryAgentStartupEvent(c.getDockerImage(), t.getView()));
                } else {
                    //docker agent for this one is either coming up online or will be dumped/stopped by ECSWatchDogJob
                }
             }
        });
        
    }

    @Override
    public void onStop() {
        LOG.info("Destroying executor on plugin stop");
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
        } finally {
            executor.shutdownNow();
        }
    }
    

}
