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
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BambooComponent
@ExportAsService({JMXAgentsService.class, LifecycleAware.class})
public class JMXAgentsService implements LifecycleAware {
    private static final Logger logger = LoggerFactory.getLogger(JMXAgentsService.class);

    private AgentCounts agentsCount;
    private ObjectName name;

    @Override
    public void onStop() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.unregisterMBean(name);
        } catch (InstanceNotFoundException | MBeanRegistrationException e) {
            logger.error("Failed to unregister mbean {}: {}", name, e.getMessage());
        }
    }

    @Override
    public void onStart() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        agentsCount = new AgentCounts();
        try {
            name = new ObjectName("com.atlassian.buildeng.isolated.docker:type=AgentCounts");
            mbs.registerMBean(agentsCount, name);
            logger.info("Successfully registered mbean {}, confirming object is not null: {}",
                    name,
                    agentsCount != null);
        } catch (MalformedObjectNameException |
                InstanceAlreadyExistsException |
                MBeanRegistrationException |
                NotCompliantMBeanException e) {
            logger.error("Failed to register mbean {}: {}", name, e.getMessage());
        }

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

    /**
     * Recalculate the number of throttled agents.
     *
     * @param agentsThrottled class holding information relating to throttled agents
     */
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
