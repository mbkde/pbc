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

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.StartTaskResult;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 *
 * @author mkleint
 */
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
        List<DockerHost> candidates = new CyclingECSScheduler(schedulerBackend).getDockerHosts(schedulerBackend.getClusterContainerInstances("", ""));
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);
        boolean thrown = false;
        try {
            scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 600, 100));
        } catch (ECSException ex) {
            thrown = true;
        } 
        avaitProcessing(scheduler); 
        assertTrue("Capacity overload", thrown);
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString());
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);
        String arn = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 110, 110)).getContainerArn();
        avaitProcessing(scheduler); 
        
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn);
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);

        String arn = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 100, 100)).getContainerArn();
        avaitProcessing(scheduler); 
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn);
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);

        String arn = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 100, 100)).getContainerArn();
        avaitProcessing(scheduler); 
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn);
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
                        ec2("id1", new Date(System.currentTimeMillis() - (1000 * 60 * 40))),
                        // 20 minute old instance i.e. in its first half of the billing cycle, should not be terminated
                        ec2("id2", new Date(System.currentTimeMillis() - (1000 * 60 * 20))),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);        
        String arn = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 100, 100)).getContainerArn();
        avaitProcessing(scheduler); 
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, times(1)).terminateInstances(anyList(), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn2", arn);
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
                        ec2("id1", new Date(System.currentTimeMillis() - (1000 * 60 * 40))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);
        String arn = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 600, 600)).getContainerArn();
        avaitProcessing(scheduler); 
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), anyString());
        assertEquals("arn1", arn);
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
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);
        Future<SchedulingResult> res = scheduler.scheduleImpl(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 199, 399));
        Future<SchedulingResult> res2 = scheduler.scheduleImpl(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a2", 1, 599, 599));
        avaitProcessing(scheduler); //wait to have the other thread start the processing
        assertEquals("arn1", res.get().getContainerArn());
        try {
            res2.get().getContainerArn();
            fail("Expected ECSException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Capacity not available"));
        }
        verify(schedulerBackend, never()).terminateInstances(anyList(), anyString());
        verify(schedulerBackend, times(1)).scaleTo(Matchers.anyInt(), anyString());
    }
    
    @Test(expected=ECSException.class)
    public void scheduleBackendFailGetContainers() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        when(backend.getClusterContainerInstances(anyString(), anyString())).thenThrow(new ECSException("error1"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend);
        SchedulingResult res = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 199, 399));
        avaitProcessing(scheduler); 
    }

        
    @Test(expected=ECSException.class)
    public void scheduleBackendFailGetInstances() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        when(backend.getClusterContainerInstances(anyString(), anyString())).thenReturn(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 600, 2000, 600),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400)
                )
        );
        when(backend.getInstances(anyList())).thenThrow(new ECSException("error2"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend);
        SchedulingResult res = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 199, 399));
        avaitProcessing(scheduler); 
        
    }
    
    @Test(expected=ECSException.class)
    public void scheduleBackendFailSchedule() throws Exception {
        SchedulerBackend backend = mock(SchedulerBackend.class);
        when(backend.getClusterContainerInstances(anyString(), anyString())).thenReturn(
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
        when(backend.schedule(anyString(), Matchers.any())).thenThrow(new ECSException("error3"));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(backend);
        SchedulingResult res = scheduler.schedule(new SchedulingRequest("cluster", "asg", UUID.randomUUID(), "a1", 1, 199, 399));
        avaitProcessing(scheduler); 
        
    }
    
    private SchedulerBackend mockBackend(List<ContainerInstance> containerInstances, List<Instance> ec2Instances) throws ECSException {
        SchedulerBackend mocked = mock(SchedulerBackend.class);
        when(mocked.getClusterContainerInstances(anyString(), anyString())).thenReturn(containerInstances);
        when(mocked.getInstances(anyList())).thenReturn(ec2Instances);
        when(mocked.schedule(anyString(), Matchers.any())).thenAnswer(new Answer<SchedulingResult>() {

            @Override
            public SchedulingResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                String foo = (String) invocationOnMock.getArguments()[0];
                return new SchedulingResult(new StartTaskResult(), foo);
            }
        });
        return mocked;
    }
    
    private ContainerInstance ci(String ec2Id, String arn, boolean connected, int regMem, int remMem, int regCpu, int remCpu) {
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
    
    private Instance ec2(String ec2id, Date launchTime) {
        return new Instance().withInstanceId(ec2id).withLaunchTime(launchTime);
    }
    
    private void avaitProcessing(CyclingECSScheduler scheduler) throws InterruptedException {
        Thread.sleep(50); //wait to have the other thread start the processing
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
    }
    
}
