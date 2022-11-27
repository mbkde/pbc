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

package com.atlassian.buildeng.kubernetes.jmx;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Object holding the queued agent gauges.
 */
public class KubeAgents implements KubeAgentsMBean {

    final AtomicLong total = new AtomicLong(0);
    final AtomicLong minute5 = new AtomicLong(0);
    final AtomicLong minute10 = new AtomicLong(0);
    final AtomicLong minute15 = new AtomicLong(0);
    final AtomicLong minute20 = new AtomicLong(0);
    final AtomicLong minute25 = new AtomicLong(0);
    final AtomicLong minute30 = new AtomicLong(0);

    @Override
    public long getQueuedTotalGauge() {
        return total.get();
    }

    @Override
    public long getQueuedFor5MinutesGauge() {
        return minute5.get();
    }

    @Override
    public long getQueuedFor10MinutesGauge() {
        return minute10.get();
    }

    @Override
    public long getQueuedFor15MinutesGauge() {
        return minute15.get();
    }

    @Override
    public long getQueuedFor20MinutesGauge() {
        return minute20.get();
    }

    @Override
    public long getQueuedFor25MinutesGauge() {
        return minute25.get();
    }

    @Override
    public long getQueuedFor30MinutesGauge() {
        return minute30.get();
    }

}
