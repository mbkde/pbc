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
package com.atlassian.buildeng.isolated.docker.jmx;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author mkleint
 */
public class AgentCounts implements AgentCountsMBean {
    
    final AtomicLong queued = new AtomicLong(0);
    final AtomicLong scheduled = new AtomicLong(0);
    final AtomicLong active = new AtomicLong(0);
    final AtomicLong cancelled = new AtomicLong(0);
    final AtomicLong timedout = new AtomicLong(0);
    final AtomicLong failed = new AtomicLong(0);

    @Override
    public long getQueuedAgentsCount() {
        return queued.get();
    }

    @Override
    public long getScheduledAgentsCount() {
        return scheduled.get();
    }

    @Override
    public long getActiveAgentsCount() {
        return active.get();
    }

    @Override
    public long getCancelledAgentsCount() {
        return cancelled.get();
    }

    @Override
    public long getTimedOutAgentsCount() {
        return timedout.get();
    }

    @Override
    public long getFailedAgentsCount() {
        return failed.get();
    }
    
}
