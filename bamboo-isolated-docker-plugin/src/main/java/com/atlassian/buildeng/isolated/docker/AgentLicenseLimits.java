/*
 * Copyright 2018 Atlassian Pty Ltd.
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

import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentLicenseLimits {

    private final Logger logger = LoggerFactory.getLogger(PreBuildQueuedEventListener.class);

    private final AgentManager agentManager;
    private final AgentCreationReschedulerImpl rescheduler;
    private final BuildQueueManager buildQueueManager;

    public AgentLicenseLimits(AgentManager agentManager, AgentCreationReschedulerImpl rescheduler,
            BuildQueueManager buildQueueManager) {
        this.agentManager = agentManager;
        this.rescheduler = rescheduler;
        this.buildQueueManager = buildQueueManager;
    }

    /**
     * check if license limit on agents was reached and reschedules the build if it was
     * @param event parameter
     * @return true when limit was reached.
     */
    boolean licenseLimitReached(RetryAgentStartupEvent event) {
        //this will sometimes for (short) periods of time allow smaller amount of agents, due to the fact that
        // we might have some agents already registered but they haven't picked up jobs yet,
        // so effectively counting them twice.
        long queued = DockerAgentBuildQueue.currentlyQueued(buildQueueManager).count();
        boolean limitReached = !agentManager.allowNewRemoteAgents((int) (1 + queued));
        if (limitReached) {
            //intentionally not creating new event object to avoid increasing the retry count.
            logger.debug("Remote agent limit reached, delaying agent creation for {}",
                    event.getContext().getResultKey());
            rescheduler.reschedule(event);
        }
        return limitReached;
    }
}
