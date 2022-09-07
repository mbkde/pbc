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

import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.kubernetes.KubernetesIsolatedDockerImpl;
import com.atlassian.buildeng.kubernetes.KubernetesWatchdog;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@BambooComponent
public class KubeJmxService implements DisposableBean, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(KubeJmxService.class);

    private KubeAgents agentsCount;
    private ObjectName name;
    
    @Override
    public void destroy() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbs.unregisterMBean(name);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        agentsCount = new KubeAgents();
        name = new ObjectName("com.atlassian.buildeng.kubernetes:type=KubeAgents");
        mbs.registerMBean(agentsCount, name);
    }
    
    /**
     * recalculate the numbers of agents queued.
     * @param buildQueueManager service
     */
    public void recalculate(BuildQueueManager buildQueueManager) {
        long now = System.currentTimeMillis();
        AtomicLong total = new AtomicLong(0);
        AtomicLong minutes5 = new AtomicLong(0);
        AtomicLong minutes10 = new AtomicLong(0);
        AtomicLong minutes15 = new AtomicLong(0);
        AtomicLong minutes20 = new AtomicLong(0);
        AtomicLong minutes25 = new AtomicLong(0);
        AtomicLong minutes30 = new AtomicLong(0);
        DockerAgentBuildQueue.currentlyQueued(buildQueueManager).forEach((CommonContext context) -> {
            CurrentResult current = context.getCurrentResult();
            String podName = current.getCustomBuildData().get(KubernetesIsolatedDockerImpl.RESULT_PREFIX
                    + KubernetesIsolatedDockerImpl.NAME);
            //kube pbc only
            if (podName != null) {
                long queueTime = Long.parseLong(current.getCustomBuildData().get(KubernetesWatchdog.QUEUE_TIMESTAMP));
                long minutes = Duration.millis(now - queueTime).getStandardMinutes();
                
                total.getAndIncrement();
                if (minutes >= 5) {
                    minutes5.getAndIncrement();
                }
                if (minutes >= 10) {
                    minutes10.getAndIncrement();
                }
                if (minutes >= 15) {
                    minutes15.getAndIncrement();
                }
                if (minutes >= 20) {
                    minutes20.getAndIncrement();
                }
                if (minutes >= 25) {
                    minutes25.getAndIncrement();
                }
                if (minutes >= 30) {
                    minutes30.getAndIncrement();
                }
            }    
        });
        agentsCount.total.getAndSet(total.get());
        agentsCount.minute5.getAndSet(minutes5.get());
        agentsCount.minute10.getAndSet(minutes10.get());
        agentsCount.minute15.getAndSet(minutes15.get());
        agentsCount.minute20.getAndSet(minutes20.get());
        agentsCount.minute25.getAndSet(minutes25.get());
        agentsCount.minute30.getAndSet(minutes30.get());
        logger.debug("total:{} 5min={} 10min={} 15min={} 20min={} 25min={} 30min={}",
                total, minutes5, minutes10, minutes15, minutes20, minutes25, minutes30);
    }
    
}
