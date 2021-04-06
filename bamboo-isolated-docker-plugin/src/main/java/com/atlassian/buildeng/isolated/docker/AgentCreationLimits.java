/*
 * Copyright 2021 Atlassian Pty Ltd.
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

import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.UUID;

public class AgentCreationLimits {
    private final GlobalConfiguration globalConfiguration;
    private final DateTime dateTime;

    private final LinkedList<AbstractMap.SimpleEntry<UUID, Date>> agentCreationQueue = new LinkedList<>();

    public AgentCreationLimits(GlobalConfiguration globalConfiguration, DateTime dateTime) {
        this.globalConfiguration = globalConfiguration;
        this.dateTime = dateTime;
    }

    /**
     * Retrieve the number of agents which can be created per minute based on the global configuration.
     * @return maximum amount of agents which can be created per minute
     */
    private Integer getMaxAgentCreationPerMinute() {
        return globalConfiguration.getMaxAgentCreationPerMinute();
    }

    /**
     * Checks whether the number of agents created is at the limit.
     * @return true if the agent creation queue is full
     */
    private boolean isAgentCreationQueueFull() {
        return agentCreationQueue.size() >= getMaxAgentCreationPerMinute();
    }

    /**
     * Inspect the first element in the queue and checks whether it was created over a minute ago.
     * @return true if the oldest agent in the queue was created more than one minute ago
     */
    private boolean oldestAgentInQueueCreatedOverOneMinuteAgo() {
        AbstractMap.SimpleEntry<UUID, Date> oldestAgent = agentCreationQueue.peek();
        return oldestAgent != null && oldestAgent.getValue().getTime() < dateTime.oneMinuteAgo();
    }

    /**
     * clear agent creation queue of all agents started more than one minute ago.
     */
    private void clearQueue() {
        while (oldestAgentInQueueCreatedOverOneMinuteAgo()) {
            agentCreationQueue.pop();
        }
    }

    /**
     * Clear the queue then determine whether it is still full.
     * @return true if agent creation limit over the past minute has been reached
     */
    public boolean creationLimitReached() {
        // clear queue of all agents started over one minute ago
        clearQueue();
        // if the queue is still full then the limit has been reached
        return isAgentCreationQueueFull();
    }

    /**
     * Add event to the back of the queue along with the current date.
     * @param event event to add to the agent creation queue
     */
    public void addToCreationQueue(RetryAgentStartupEvent event) {
        agentCreationQueue.add(new AbstractMap.SimpleEntry<>(event.getUniqueIdentifier(), new Date()));
    }

    /**
     * Remove given event from the queue.
     * @param event to remove from the agent creation queue
     */
    public void removeEventFromQueue(RetryAgentStartupEvent event) {
        Iterator<AbstractMap.SimpleEntry<UUID, Date>> it = agentCreationQueue.iterator();
        while (it.hasNext()) {
            AbstractMap.SimpleEntry<UUID, Date> agent = it.next();
            if (agent.getKey() == event.getUniqueIdentifier()) {
                it.remove();
                break;
            }
        }
    }
}
