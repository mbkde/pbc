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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.atlassian.buildeng.ecs.GlobalConfiguration;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.google.common.collect.Sets;
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
import org.mockito.Matchers;

import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
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
                    ci("id1", "arn1", true, 2000, 100, 2000, 500),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        Collection<DockerHost> candidates = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig()).loadHosts("", new AutoScalingGroup()).allUsable();
        //100 & 100 means all are viable candidates
        Optional<DockerHost> candidate = CyclingECSScheduler.selectHost(candidates, 100, 100);
        assertTrue(candidate.isPresent());
        assertEquals("id1", candidate.get().getInstanceId()); //most utilized
        
        //300 && 100 means only id3,4,5 are viable
        candidate = CyclingECSScheduler.selectHost(candidates, 300, 100);
        assertTrue(candidate.isPresent());
        assertEquals("id3", candidate.get().getInstanceId()); //most utilized
        
        //300 && 200 means only id3,4 are viable
        candidate = CyclingECSScheduler.selectHost(candidates, 300, 200);
        assertTrue(candidate.isPresent());
        assertEquals("id3", candidate.get().getInstanceId()); //most utilized
        
        //100 && 500 means only id1 are viable
        candidate = CyclingECSScheduler.selectHost(candidates, 100, 500);
        assertTrue(candidate.isPresent());
        assertEquals("id1", candidate.get().getInstanceId());
    }

    @Test
    public void scheduleScaleUp() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 100, 2000, 500),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 600, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), anyString());
    }
    
    @Test
    public void scheduleNoScaling() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 100, 2000, 500),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 110, 110, null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        awaitProcessing(scheduler);
        
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }
    
    @Test
    public void scheduleTerminateStale() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 2000, 2000, 2000),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        ec2("id1", new Date(System.currentTimeMillis() - (CyclingECSScheduler.DEFAULT_STALE_PERIOD.toMillis() + 1000))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());

        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 100, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }
    
    @Test
    public void scheduleStaleNotLoaded() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 100, 2000, 100),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        ec2("id1", new Date(System.currentTimeMillis() - (CyclingECSScheduler.DEFAULT_STALE_PERIOD.toMillis() + 1000))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());

        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 100, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }    
    
    @Test
    public void scheduleTerminatingOfIdleInSecondHalfOfBillingCycle() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 2000, 2000, 2000),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        // 40 minute old instance, i.e. in its second half of the billing cycle and should be terminated
                        ec2("id1", new Date(System.currentTimeMillis() - 1000 * 60 * 40)),
                        // 20 minute old instance i.e. in its first half of the billing cycle, should not be terminated
                        ec2("id2", new Date(System.currentTimeMillis() - 1000 * 60 * 20)),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicReference<String> arn = new AtomicReference<>();

        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 100, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
    }
    
    @Test
    public void scheduleUnusedFreshIsSelected() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 2000, 2000, 2000),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 600, 600, null), new SchedulingCallback() {
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
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn1", arn.get());
    }
    
    
    @Test
    public void scheduleRequestCoalesce() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 600, 2000, 600),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicBoolean thrown = new AtomicBoolean(false);
        AtomicReference<String> arn = new AtomicReference<>();
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 199, 399, null), new SchedulingCallback() {
            @Override
            public void handle(SchedulingResult result) {
                arn.set(result.getContainerArn());
            }

            @Override
            public void handle(ECSException exception) {
            }
        });
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a2", 1, 599, 599, null), new SchedulingCallback() {
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
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, times(1)).scaleTo(Matchers.anyInt(), anyString());
    }
    
    @Test
    public void scheduleBackendFailGetContainers() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        mockASG(Sets.newHashSet("a1"), backend);
        when(backend.getClusterContainerInstances(anyString())).thenThrow(new ECSException("error1"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend, mockGlobalConfig());
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 199, 399, null), new SchedulingCallback() {
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
                    ci("id1", "arn1", true, 2000, 600, 2000, 600),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400)
                )
        );
        mockASG(Sets.newHashSet("id1", "id2"), backend);
        when(backend.getInstances(anyList())).thenThrow(new ECSException("error2"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend, mockGlobalConfig());
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 199, 399, null), new SchedulingCallback() {
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
                    ci("id1", "arn1", true, 2000, 600, 2000, 600),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400)
                    )
        );
        when(backend.getInstances(anyList())).thenReturn(Arrays.asList(
                    ec2("id1", new Date()),
                    ec2("id2", new Date())
                )
        );
        mockASG(Sets.newHashSet("id1", "id2"), backend);
        when(backend.schedule(anyString(), anyString(), Matchers.any(), Matchers.any())).thenThrow(new ECSException("error3"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend, mockGlobalConfig());
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 199, 399, null), new SchedulingCallback() {
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
                    ci("id1", "arn1", false, 2000, 100, 2000, 500),
                    ci("id2", "arn2", false, 2000, 200, 2000, 400),
                    ci("id3", "arn3", false, 2000, 800, 2000, 800),
                    ci("id4", "arn4", false, 2000, 800, 2000, 800),
                    ci("id5", "arn5", false, 2000, 800, 2000, 800)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        
        populateDisconnectedCacheWithRipeHosts(scheduler);
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 600, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString(), Matchers.eq(false));
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        //we don't scale up, because the broken, reprovisioned instances are enough.
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
    }    
    
    @Test
    public void noTerminationOnFreshDisconnected() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", false, 2000, 100, 2000, 500),
                    ci("id2", "arn2", false, 2000, 200, 2000, 400),
                    ci("id3", "arn3", false, 2000, 800, 2000, 800),
                    ci("id4", "arn4", false, 2000, 800, 2000, 800),
                    ci("id5", "arn5", false, 2000, 800, 2000, 800)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 600, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(false));
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        //we do scale up, because we have all disconnected agents that we cannot terminate yet. (might be a flake)
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), anyString());
    }    
    

    private void populateDisconnectedCacheWithRipeHosts(CyclingECSScheduler scheduler) throws ECSException {
        DockerHosts hosts = scheduler.loadHosts("", new AutoScalingGroup());
        for (DockerHost disc : hosts.agentDisconnected()) {
            scheduler.disconnectedAgentsCache.put(disc, new Date(new Date().getTime() - 1000 * 60 * 2));
        }
    }
    
   @Test
    public void scheduleScaleUpWithDisconnectedTerminationFailed() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", false, 2000, 100, 2000, 500),
                    ci("id2", "arn2", false, 2000, 200, 2000, 400),
                    ci("id3", "arn3", false, 2000, 800, 2000, 800),
                    ci("id4", "arn4", false, 2000, 800, 2000, 800),
                    ci("id5", "arn5", false, 2000, 800, 2000, 800)
                    ),
                Arrays.asList(
                        ec2("id1", new Date()),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        populateDisconnectedCacheWithRipeHosts(scheduler);
        Mockito.doThrow(new ECSException("error")).when(schedulerBackend).terminateInstances(anyList(), anyString(), eq(false));
        AtomicBoolean thrown = new AtomicBoolean(false);
        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 600, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString(), Matchers.eq(false));
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        //we have to scale up as we failed to terminate the disconnected agents in some way
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), anyString());
    } 
    
    
    @Test
    public void scheduleTerminatingOfNonASGContainer() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 2000, 2000, 2000),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 2000, 2000, 2000) //unused stale only gets killed
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend, mockGlobalConfig());
        AtomicReference<String> arn = new AtomicReference<>();

        scheduler.schedule(new SchedulingRequest(UUID.randomUUID(), "a1", 1, 100, 100, null), new SchedulingCallback() {
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
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString(), Matchers.eq(true));
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn.get());
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
    
    static ContainerInstance ci(String ec2Id, String arn, boolean connected, int regMem, int remMem, int regCpu, int remCpu) {
        return new ContainerInstance()
                .withEc2InstanceId(ec2Id)
                .withContainerInstanceArn(arn)
                .withAgentConnected(connected)
                .withRegisteredResources(
                        new Resource().withName("MEMORY").withIntegerValue(regMem),
                        new Resource().withName("CPU").withIntegerValue(regCpu))
                .withRemainingResources(
                        new Resource().withName("MEMORY").withIntegerValue(remMem),
                        new Resource().withName("CPU").withIntegerValue(remCpu));
        
    }
    
    static Instance ec2(String ec2id, Date launchTime) {
        return new Instance().withInstanceId(ec2id).withLaunchTime(launchTime).withInstanceType("m4.4xlarge");
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
}
