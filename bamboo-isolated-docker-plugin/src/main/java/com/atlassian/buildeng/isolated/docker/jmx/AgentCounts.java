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

package com.atlassian.buildeng.isolated.docker.jmx;

import java.util.concurrent.atomic.AtomicLong;

public class AgentCounts implements AgentCountsMBean {
    
    final AtomicLong queued = new AtomicLong(0);
    final AtomicLong scheduled = new AtomicLong(0);
    final AtomicLong active = new AtomicLong(0);
    final AtomicLong cancelled = new AtomicLong(0);
    final AtomicLong timedOut = new AtomicLong(0);
    final AtomicLong failed = new AtomicLong(0);
    final AtomicLong throttledTotal = new AtomicLong(0);
    final AtomicLong throttled5Minutes = new AtomicLong(0);
    final AtomicLong throttled10Minutes = new AtomicLong(0);
    final AtomicLong throttled15Minutes = new AtomicLong(0);
    final AtomicLong throttled20Minutes = new AtomicLong(0);
    final AtomicLong throttled25Minutes = new AtomicLong(0);
    final AtomicLong throttled30Minutes = new AtomicLong(0);

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
        return timedOut.get();
    }

    @Override
    public long getFailedAgentsCount() {
        return failed.get();
    }

    @Override
    public long getAgentsThrottledTotalGauge() {
        return throttledTotal.get();
    }

    @Override
    public long getThrottledFor5MinutesGauge() {
        return throttled5Minutes.get();
    }

    @Override
    public long getThrottledFor10MinutesGauge() {
        return throttled10Minutes.get();
    }

    @Override
    public long getThrottledFor15MinutesGauge() {
        return throttled15Minutes.get();
    }

    @Override
    public long getThrottledFor20MinutesGauge() {
        return throttled20Minutes.get();
    }

    @Override
    public long getThrottledFor25MinutesGauge() {
        return throttled25Minutes.get();
    }

    @Override
    public long getThrottledFor30MinutesGauge() {
        return throttled30Minutes.get();
    }
}
