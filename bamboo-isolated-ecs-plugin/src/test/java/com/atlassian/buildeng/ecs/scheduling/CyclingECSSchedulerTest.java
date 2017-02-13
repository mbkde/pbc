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
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.atlassian.buildeng.ecs.GlobalConfiguration;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsStaleAsgInstanceEvent;
import com.atlassian.event.api.EventPublisher;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 *
 * @author mkleint
 */
@SuppressWarnings("unchecked")
public class CyclingECSSchedulerTest {
    
    public CyclingECSSchedulerTest() {
    }

    @Test
    public void testSelectHost() throws ECSException {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 50, 50),
                    ci("id2", "arn2", true, 40, 40),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 20, 20),
                    ci("id5", "arn5", true, 10, 10)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        Collection<DockerHost> candidates = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class)).loadHosts("", new AutoScalingGroup()).allUsable();
        //5 cpu & 5 mem means all are viable candidates
        Optional<DockerHost> candidate = CyclingECSScheduler.selectHost(candidates, mem(5), cpu(5));
        assertTrue(candidate.isPresent());
        assertEquals("id1", candidate.get().getInstanceId()); //most utilized
        
        //65 cpu && 65 mem means only id3,4,5 are viable
        candidate = CyclingECSScheduler.selectHost(candidates, mem(65), cpu(65));
        assertTrue(candidate.isPresent());
        assertEquals("id3", candidate.get().getInstanceId()); //most utilized
        
        //75 cpu && 75 mem means only id4,5 are viable
        candidate = CyclingECSScheduler.selectHost(candidates, mem(75), cpu(75));
        assertTrue(candidate.isPresent());
        assertEquals("id4", candidate.get().getInstanceId()); //most utilized
        
        candidate = CyclingECSScheduler.selectHost(candidates, mem(85), cpu(85));
        assertTrue(candidate.isPresent());
        assertEquals("id5", candidate.get().getInstanceId());
    }

    @Test
    public void scheduleScaleUp() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 10, 50),
                    ci("id2", "arn2", true, 20, 40),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 40, 20),
                    ci("id5", "arn5", true, 50, 10)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(75), mem(75), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Capacity overload", thrown.get());
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), anyString());
    }
    
    @Test
    public void scheduleNoScaling() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 10, 50),
                    ci("id2", "arn2", true, 20, 40),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 40, 20),
                    ci("id5", "arn5", true, 50, 10)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1,  cpu(55), mem(55), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn4", arn.get());
    }
    
    @Test
    public void scheduleTerminateStale() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 0, 0),
                    ci("id2", "arn2", true, 50, 50),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 40, 20),
                    ci("id5", "arn5", true, 50, 10)
                    ),
                Arrays.asList(
                        ec2("id1", new Date(System.currentTimeMillis() - (CyclingECSScheduler.DEFAULT_STALE_PERIOD.toMillis() + 1000))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));

        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, times(1)).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }
    
    @Test
    public void scheduleStaleNotLoaded() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 10, 10),
                    ci("id2", "arn2", true, 40, 40),
                    ci("id3", "arn3", true, 10, 10),
                    ci("id4", "arn4", true, 40, 20),
                    ci("id5", "arn5", true, 30, 10)
                    ),
                Arrays.asList(
                        ec2("id1", new Date(System.currentTimeMillis() - (CyclingECSScheduler.DEFAULT_STALE_PERIOD.toMillis() + 1000))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));

        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }    
    
    @Test
    public void scheduleTerminatingOfIdleInLastQuarterOfBillingCycle() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 0, 0),
                    ci("id2", "arn2", true, 60, 60),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 30, 30),
                    ci("id5", "arn5", true, 10, 10)
                    ),
                Arrays.asList(
                        // 55 minute old instance, i.e. in its final stage of the billing cycle and should be terminated
                        ec2("id1", new Date(System.currentTimeMillis() - 1000 * 60 * 55)),
                        // 45 minute old instance i.e. in its working stage of the billing cycle, should not be terminated
                        ec2("id2", new Date(System.currentTimeMillis() - 1000 * 60 * 45)),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicReference<String> arn = new AtomicReference<>();

        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(5), mem(5), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, times(1)).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }

    @Test
    public void scheduleTerminationKeepFreeRation() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true,  0, 0),
                    ci("id2", "arn2", true,  80, 80),
                    ci("id3", "arn3", true,  80, 80),
                    ci("id4", "arn4", true,  80, 80),
                    ci("id5", "arn5", true,  80, 80),
                    ci("id6", "arn6", true,  0, 0),
                    ci("id7", "arn7", true,  0, 0)
                    ),
                Arrays.asList(
                        // 55 minute old instance, i.e. in its final stage of the billing cycle and should be terminated
                        ec2("id1", new Date(System.currentTimeMillis() - 1000 * 60 * 55)),
                        // 45 minute old instance i.e. in its working stage of the billing cycle, should not be terminated
                        ec2("id2", new Date(System.currentTimeMillis() - 1000 * 60 * 45)),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date()),
                        // 55 minute old instance, i.e. in its final stage of the billing cycle and should be terminated
                        ec2("id6", new Date(System.currentTimeMillis() - 1000 * 60 * 55)),
                        // 55 minute old instance, i.e. in its final stage of the billing cycle and should be terminated
                        ec2("id7", new Date(System.currentTimeMillis() - 1000 * 60 * 55))
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicReference<String> arn = new AtomicReference<>();

        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);

        verify(schedulerBackend, times(1)).terminateAndDetachInstances(argThat(new IsListOfTwoElements()), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }


    
    @Test
    public void scheduleUnusedFreshIsSelected() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 60, 60),
                    ci("id2", "arn2", true, 20, 20),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 40, 40),
                    ci("id5", "arn5", true, 50, 50)
                    ),
                Arrays.asList(
                        // 40 minutes old instance (past halfway in billing cycle)\
                        // Should pick up the job anyway (isn't stale)
                        ec2("id1", new Date(System.currentTimeMillis() - 1000 * 60 * 40)),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(39), mem(39), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn1", arn.get());
    }
    
    
    @Test
    public void scheduleRequestCoalesce() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 60, 60),
                    ci("id2", "arn2", true, 40, 40)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicBoolean thrown = new AtomicBoolean(false);
        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a2", 1, cpu(65), mem(65), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler); //wait to have the other thread start the processing
        assertEquals("arn1", arn.get());
        assertTrue("Exception Thrown correctly", thrown.get());
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, times(1)).scaleTo(Matchers.anyInt(), anyString());
    }
    
    @Test
    public void scheduleBackendFailGetContainers() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        mockASG(Sets.newHashSet("a1"), backend);
        when(backend.getClusterContainerInstances(anyString())).thenThrow(new ECSException("error1"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Exception Thrown correctly", thrown.get());
    }

        
    @Test
    public void scheduleBackendFailGetInstances() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        when(backend.getClusterContainerInstances(anyString())).thenReturn(
                Arrays.asList(
                    ci("id1", "arn1", true, 60, 60),
                    ci("id2", "arn2", true, 20, 40)
                )
        );
        mockASG(Sets.newHashSet("id1", "id2"), backend);
        when(backend.getInstances(anyList())).thenThrow(new ECSException("error2"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Exception Thrown correctly", thrown.get());
    }
    
    @Test
    public void scheduleBackendFailSchedule() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        when(backend.getClusterContainerInstances(anyString())).thenReturn(
                Arrays.asList(
                    ci("id1", "arn1", true, 60, 60),
                    ci("id2", "arn2", true, 20, 40)
                    )
        );
        when(backend.getInstances(anyList())).thenReturn(Arrays.asList(
                    ec2("id1", new Date()),
                    ec2("id2", new Date())
                )
        );
        mockASG(Sets.newHashSet("id1", "id2"), backend);
        when(backend.schedule(anyString(), anyString(), Matchers.any(), Matchers.any())).thenThrow(new ECSException("error3"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(39), mem(19), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Exception Thrown correctly", thrown.get());
        
    }
    
    
    @Test
    public void scheduleScaleUpWithDisconnected() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", false, 10, 10),
                    ci("id2", "arn2", false, 20, 20),
                    ci("id3", "arn3", false, 80, 80),
                    ci("id4", "arn4", false, 100, 100),
                    ci("id5", "arn5", false, 0, 0)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        
        populateDisconnectedCacheWithRipeHosts(scheduler);
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(60), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Capacity overload", thrown.get());
        verify(schedulerBackend, times(1)).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(false), anyString());
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        //we don't scale up, because the broken, reprovisioned instances are enough.
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
    }    
    
    @Test
    public void noTerminationOnFreshDisconnected() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", false, 10, 50),
                    ci("id2", "arn2", false, 20, 20),
                    ci("id3", "arn3", false, 0, 0),
                    ci("id4", "arn4", false, 100, 100),
                    ci("id5", "arn5", false, 50, 50)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(60), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Capacity overload", thrown.get());
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(false), anyString());
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        //we do scale up, because we have all disconnected agents that we cannot terminate yet. (might be a flake)
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), anyString());
    }    
    

    private void populateDisconnectedCacheWithRipeHosts(CyclingECSScheduler scheduler) throws ECSException {
        DockerHosts hosts = scheduler.loadHosts("", new AutoScalingGroup());
        for (DockerHost disc : hosts.agentDisconnected()) {
            scheduler.disconnectedAgentsCache.put(disc, new Date(new Date().getTime() - 1000 * 60 * 2 * CyclingECSScheduler.TIMEOUT_IN_MINUTES_TO_KILL_DISCONNECTED_AGENT));
        }
    }
    
   @Test
    public void scheduleScaleUpWithDisconnectedTerminationFailed() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", false, 10, 20),
                    ci("id2", "arn2", false, 10, 10),
                    ci("id3", "arn3", false, 80, 80),
                    ci("id4", "arn4", false, 50, 50),
                    ci("id5", "arn5", false, 50, 50)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        populateDisconnectedCacheWithRipeHosts(scheduler);
        Mockito.doThrow(new ECSException("error")).when(schedulerBackend).terminateAndDetachInstances(anyList(), anyString(), eq(false), anyString());
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(60), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
                thrown.set(true);
            }
        });
        awaitProcessing(scheduler);
        assertTrue("Capacity overload", thrown.get());
        verify(schedulerBackend, times(1)).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(false), anyString());
        verify(schedulerBackend, never()).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        //we have to scale up as we failed to terminate the disconnected agents in some way
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), anyString());
    } 
    
    
    @Test
    public void scheduleTerminatingOfNonASGContainer() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 0, 0),
                    ci("id2", "arn2", true, 20, 40),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 40, 20),
                    ci("id5", "arn5", true, 0, 0) //unused stale only gets killed
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ),
                //id5 is not in ASG
                Sets.newHashSet("id1", "id2", "id3", "id4"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), mock(EventPublisher.class));
        AtomicReference<String> arn = new AtomicReference<>();

        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(59), mem(79), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        //TODO how to verify that it contained id5?
        verify(schedulerBackend, times(1)).terminateAndDetachInstances(anyList(), anyString(), Matchers.eq(true), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }

    @Test
    public void asgIsLonely() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 0, 0),
                    ci("id2", "arn2", true, 20, 40),
                    ci("id3", "arn3", true, 30, 30),
                    ci("id4", "arn4", true, 20, 20),
                    ci("id5", "arn5", true, 0, 0) //unused stale only gets killed
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date()),
                        ec2("id6", new Date(System.currentTimeMillis() - Duration.ofHours(1).toMillis()))
                ),
                //id5 is not in ASG
                Sets.newHashSet("id1", "id2", "id3", "id4", "id5", "id6"));
        final EventPublisher eventPublisher = mock(EventPublisher.class);
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig(), eventPublisher);

        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, cpu(10), mem(10), null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);

        verify(eventPublisher, times(1)).publish(any(DockerAgentEcsStaleAsgInstanceEvent.class));

    }
    
    private SchedulerBackend mockBackend(List<ContainerInstance> containerInstances, List<Instance> ec2Instances) throws ECSException {
        return mockBackend(containerInstances, ec2Instances, ec2Instances.stream().map(Instance::getInstanceId).collect(Collectors.toSet()));
    }
    
    private SchedulerBackend mockBackend(List<ContainerInstance> containerInstances, List<Instance> ec2Instances, Set<String> asgInstances) throws ECSException {
        SchedulerBackend mocked = mock(SchedulerBackend.class);
        when(mocked.getClusterContainerInstances(anyString())).thenReturn(containerInstances);
        when(mocked.getInstances(anyList())).thenReturn(ec2Instances);
        mockASG(asgInstances, mocked);
        when(mocked.schedule(anyString(), anyString(), Matchers.any(), Matchers.any())).thenAnswer(invocationOnMock -> {
            String foo = (String) invocationOnMock.getArguments()[0];
            return new SchedulingResult(new StartTaskResult(), foo);
        });
        return mocked;
    }

    private void mockASG(Set<String> asgInstances, SchedulerBackend mocked) throws ECSException {
        AutoScalingGroup asg = new AutoScalingGroup();
        asg.setMaxSize(50);
        asg.setDesiredCapacity(asgInstances.size());
        asg.setInstances(asgInstances.stream().map((String t) -> {
            com.amazonaws.services.autoscaling.model.Instance i = new com.amazonaws.services.autoscaling.model.Instance();
            i.setInstanceId(t);
            return i;
        }).collect(Collectors.toList()));
        when(mocked.describeAutoScalingGroup(anyString())).thenReturn(asg);
    }
    
    static ContainerInstance ci(String ec2Id, String arn, boolean connected, int usedMemPercentage, int usedCpuPercentage) {
        return new ContainerInstance()
                .withEc2InstanceId(ec2Id)
                .withContainerInstanceArn(arn)
                .withAgentConnected(connected)
                .withRegisteredResources(
                        new Resource().withName("MEMORY").withIntegerValue(ECSInstance.DEFAULT_INSTANCE.getMemory()),
                        new Resource().withName("CPU").withIntegerValue(ECSInstance.DEFAULT_INSTANCE.getCpu()))
                .withRemainingResources(
                        new Resource().withName("MEMORY").withIntegerValue(mem(100 - usedMemPercentage)),
                        new Resource().withName("CPU").withIntegerValue(cpu(100 - usedCpuPercentage)));
        
    }
    
    static Instance ec2(String ec2id, Date launchTime) {
        return new Instance().withInstanceId(ec2id).withLaunchTime(launchTime).withInstanceType(ECSInstance.DEFAULT_INSTANCE.getName());
    }
    
    private void awaitProcessing(CyclingECSScheduler scheduler) throws InterruptedException {
        Thread.sleep(100); //wait to have the other thread start the processing
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(500, TimeUnit.MILLISECONDS); //make sure the background thread finishes
    }
    
    
    private GlobalConfiguration mockGlobalConfig() {
        GlobalConfiguration mock = mock(GlobalConfiguration.class);
        when(mock.getCurrentCluster()).thenReturn("cluster");
        when(mock.getCurrentASG()).thenReturn("asg");
        return mock;
    }

    static int mem(int percentage) {
        return ECSInstance.DEFAULT_INSTANCE.getMemory() * percentage / 100;
    }

    static int cpu(int percentage) {
        return ECSInstance.DEFAULT_INSTANCE.getCpu() * percentage / 100;
    }

    class IsListOfTwoElements extends ArgumentMatcher<List> {
     @Override
     public boolean matches(Object list) {
         return ((List) list).size() == 2;
     }
 }
}
