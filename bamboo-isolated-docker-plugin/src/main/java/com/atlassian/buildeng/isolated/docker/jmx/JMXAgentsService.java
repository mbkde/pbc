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

import com.atlassian.buildeng.isolated.docker.AgentsThrottled;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class JMXAgentsService implements DisposableBean, InitializingBean {

    private AgentCounts agentsCount;
    private ObjectName name;

    @Override
    public void destroy() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbs.unregisterMBean(name);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        agentsCount = new AgentCounts();
        name = new ObjectName("com.atlassian.buildeng.docker:type=AgentCounts");
        mbs.registerMBean(agentsCount, name);
    }

    public void incrementQueued() {
        agentsCount.queued.incrementAndGet();
    }

    public void incrementCancelled() {
        agentsCount.cancelled.incrementAndGet();
    }

    public void incrementTimedOut() {
        agentsCount.timedOut.incrementAndGet();
    }

    public void incrementFailed() {
        agentsCount.failed.incrementAndGet();
    }

    public void incrementScheduled() {
        agentsCount.scheduled.incrementAndGet();
    }
    
    public void incrementActive() {
        agentsCount.active.incrementAndGet();
    }

    public void recalculateThrottle(AgentsThrottled agentsThrottled) {
        agentsCount.throttledTotal.set(agentsThrottled.getTotalAgentsThrottled());
        agentsCount.throttled5Minutes.set(agentsThrottled.numAgentsThrottledLongerThanMinutes(5));
        agentsCount.throttled10Minutes.set(agentsThrottled.numAgentsThrottledLongerThanMinutes(10));
        agentsCount.throttled15Minutes.set(agentsThrottled.numAgentsThrottledLongerThanMinutes(15));
        agentsCount.throttled20Minutes.set(agentsThrottled.numAgentsThrottledLongerThanMinutes(20));
        agentsCount.throttled25Minutes.set(agentsThrottled.numAgentsThrottledLongerThanMinutes(25));
        agentsCount.throttled30Minutes.set(agentsThrottled.numAgentsThrottledLongerThanMinutes(30));
    }
    
}
