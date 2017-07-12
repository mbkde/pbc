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
package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import static com.atlassian.buildeng.ecs.scheduling.CyclingECSSchedulerTest.cpu;
import static com.atlassian.buildeng.ecs.scheduling.CyclingECSSchedulerTest.mem;

import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
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
                CyclingECSSchedulerTest.ci("id1", "arn1", true, mem(10), cpu(50)),
                CyclingECSSchedulerTest.ec2("id1", new Date()), true);
        DockerHost dh2 = new DockerHost(
                CyclingECSSchedulerTest.ci("id2", "arn2", false, mem(10), cpu(50)),
                CyclingECSSchedulerTest.ec2("id2", new Date()), true);
        DockerHost dh3 = new DockerHost(
                CyclingECSSchedulerTest.ci("id3", "arn3", true, mem(10), cpu(50)),
                CyclingECSSchedulerTest.ec2("id3", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())), true);
        DockerHost dh4 = new DockerHost(
                CyclingECSSchedulerTest.ci("id4", "arn4", false, mem(10), cpu(50)),
                CyclingECSSchedulerTest.ec2("id4", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())), true);
        
        DockerHosts hosts = new DockerHosts(
                Lists.newArrayList(dh1, dh2, dh3, dh4), Duration.ofDays(1), new AutoScalingGroup(), "cn");

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
    
    
    @Test
    public void someNotInASG() throws Exception {
        // all are empty, they differ in staleness + ASG containment
        DockerHost dh1 = new DockerHost(
                CyclingECSSchedulerTest.ci("id1", "arn1", true, 0, 0),
                CyclingECSSchedulerTest.ec2("id1", new Date()), true);
        DockerHost dh2 = new DockerHost(
                CyclingECSSchedulerTest.ci("id2", "arn2", true, 0, 0),
                CyclingECSSchedulerTest.ec2("id2", new Date()), false);
        DockerHost dh3 = new DockerHost(
                CyclingECSSchedulerTest.ci("id3", "arn3", true, 0, 0),
                CyclingECSSchedulerTest.ec2("id3", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())), true);
        DockerHost dh4 = new DockerHost(
                CyclingECSSchedulerTest.ci("id4", "arn4", true, 0, 0),
                CyclingECSSchedulerTest.ec2("id4", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())), false);
        DockerHosts hosts = new DockerHosts(
                Lists.newArrayList(dh1, dh2, dh3, dh4), Duration.ofDays(1), new AutoScalingGroup(), "cn");
        
        assertTrue(hosts.allUsable().contains(dh1));
        assertTrue(hosts.allUsable().contains(dh2));
        assertTrue(hosts.allUsable().contains(dh3));
        assertTrue(hosts.allUsable().contains(dh4));

        assertTrue(hosts.fresh().contains(dh1));
        assertFalse(hosts.fresh().contains(dh2));
        assertFalse(hosts.fresh().contains(dh3));
        assertFalse(hosts.fresh().contains(dh4));
        
        assertFalse(hosts.unusedStale().contains(dh1));
        assertTrue(hosts.unusedStale().contains(dh2));
        assertTrue(hosts.unusedStale().contains(dh3));
        assertTrue(hosts.unusedStale().contains(dh4));
    }

    @Test
    public void someDraining() throws Exception {
        DockerHost dh1 = new DockerHost(
                CyclingECSSchedulerTest.ci("id1", "arn1", true, 0, 0),
                CyclingECSSchedulerTest.ec2("id1", new Date()), true);
        DockerHost dh2 = new DockerHost(
                CyclingECSSchedulerTest.ci("id2", "arn2", true, 0, 0, ContainerInstanceStatus.DRAINING.toString()),
                CyclingECSSchedulerTest.ec2("id2", new Date()), false);
        DockerHost dh3 = new DockerHost(
                CyclingECSSchedulerTest.ci("id3", "arn3", true, 0, 0),
                CyclingECSSchedulerTest.ec2("id3", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())), true);
        DockerHost dh4 = new DockerHost(
                CyclingECSSchedulerTest.ci("id4", "arn4", true, 0, 0, ContainerInstanceStatus.DRAINING.toString()),
                CyclingECSSchedulerTest.ec2("id4", new Date(new Date().getTime() - Duration.ofDays(2).toMillis())), true);

        DockerHosts hosts = new DockerHosts(
                Lists.newArrayList(dh1, dh2, dh3, dh4), Duration.ofDays(1), new AutoScalingGroup(), "cn");

        assertTrue(hosts.fresh().contains(dh1));
        assertFalse(hosts.fresh().contains(dh2));
        assertFalse(hosts.fresh().contains(dh3));
        assertFalse(hosts.fresh().contains(dh4));

        assertFalse(hosts.unusedStale().contains(dh1));
        assertTrue(hosts.unusedStale().contains(dh2));
        assertTrue(hosts.unusedStale().contains(dh3));
        assertTrue(hosts.unusedStale().contains(dh4));
    }
}
