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
package com.atlassian.buildeng.ecs.scheduling;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mkleint
 */
public class DockerHostsTest {
    
 
    @Test
    public void someDisconnected() throws Exception {
        DockerHost dh1 = new DockerHost(
                CyclingECSSchedulerTest.ci("id1", "arn1", true, 2000, 100, 2000, 500), 
                CyclingECSSchedulerTest.ec2("id1", new Date()));
        DockerHost dh2 = new DockerHost(
                CyclingECSSchedulerTest.ci("id2", "arn2", false, 2000, 100, 2000, 500), 
                CyclingECSSchedulerTest.ec2("id2", new Date()));
        DockerHost dh3 = new DockerHost(
                CyclingECSSchedulerTest.ci("id3", "arn3", true, 2000, 100, 2000, 500), 
                CyclingECSSchedulerTest.ec2("id3", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())));
        DockerHost dh4 = new DockerHost(
                CyclingECSSchedulerTest.ci("id4", "arn4", false, 2000, 100, 2000, 500), 
                CyclingECSSchedulerTest.ec2("id4", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())));
        
        DockerHosts hosts = new DockerHosts(Lists.newArrayList(dh1, dh2, dh3, dh4), Duration.ofDays(1));
        assertFalse(hosts.agentDisconnected().contains(dh1));
        assertTrue(hosts.agentDisconnected().contains(dh2));
        assertFalse(hosts.agentDisconnected().contains(dh3));
        assertTrue(hosts.agentDisconnected().contains(dh4));
        
        assertTrue(hosts.allUsable().contains(dh1));
        assertFalse(hosts.allUsable().contains(dh2));
        assertTrue(hosts.allUsable().contains(dh3));
        assertFalse(hosts.allUsable().contains(dh4));

        assertTrue(hosts.fresh().contains(dh1));
        assertFalse(hosts.fresh().contains(dh2));
        assertFalse(hosts.fresh().contains(dh3));
        assertFalse(hosts.fresh().contains(dh4));
        
    }
    
}