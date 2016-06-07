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
package com.atlassian.buildeng.ecs;

import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * when plugins get reloaded, the retry events of the old incarnation get lost,
 * this makes sure we retrigger the agent request cycle.
 * @author mkleint
 */
public class PluginRestartQueueMonitor implements LifecycleAware {
    private final static Logger logger = LoggerFactory.getLogger(PluginRestartQueueMonitor.class);

    private final BuildQueueManager buildQueueManager;
    private final EventPublisher eventPublisher;

    public PluginRestartQueueMonitor(BuildQueueManager buildQueueManager, EventPublisher eventPublisher) {
        this.buildQueueManager = buildQueueManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onStart() {
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, (BuildQueueManager.QueueItemView<CommonContext> input) -> input);
        queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            Map<String, String> bd = t.getView().getCurrentResult().getCustomBuildData();
            Configuration c = Configuration.forContext(t.getView());
            if (c.isEnabled()) {
                String taskArn = bd.get(Constants.RESULT_PREFIX + Constants.RESULT_PART_TASKARN);
                if (taskArn == null) {
                    //we need to restart this guy.
                    logger.info("Restarted scheduling of {} after plugin restart.", t.getView().getResultKey());
                    eventPublisher.publish(new RetryAgentStartupEvent(c.getDockerImage(), t.getView()));
                } else {
                    //docker agent for this one is either coming up online or will be dumped/stopped by ECSWatchDogJob
                }
             }
        });
        
    }

    @Override
    public void onStop() {
    }
}
