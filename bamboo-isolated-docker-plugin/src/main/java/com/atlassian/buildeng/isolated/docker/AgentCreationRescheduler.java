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
import com.atlassian.buildeng.isolated.docker.events.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 *
 * @author mkleint
 */
public class AgentCreationRescheduler implements DisposableBean  {
    private final Logger LOG = LoggerFactory.getLogger(AgentCreationRescheduler.class);
    
    private final EventPublisher eventPublisher;
    private final ScheduledExecutorService executor = NamedExecutors.newScheduledThreadPool(1, "Docker Agent Retry Pool");
    private static final int MAX_RETRY_COUNT = 20;

    private AgentCreationRescheduler(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
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
    

}
