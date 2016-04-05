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
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.mockito.Matchers;
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
        List<DockerHost> candidates = new CyclingECSScheduler(schedulerBackend).getDockerHosts(schedulerBackend.getClusterContainerInstances(""));
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
            scheduler.schedule("cluster", "asg", 600, 100);
        } catch (ECSException ex) {
            thrown = true;
        } 
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        assertTrue("Capacity overload", thrown);
        verify(schedulerBackend, never()).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, times(1)).scaleTo(Matchers.eq(6), Matchers.anyString());
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
        String arn = scheduler.schedule("cluster", "asg", 110, 110);
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        
        verify(schedulerBackend, never()).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), Matchers.anyString());
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
        String arn = scheduler.schedule("cluster", "asg", 100, 100);
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, times(1)).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), Matchers.anyString());
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
        String arn = scheduler.schedule("cluster", "asg", 100, 100);
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, never()).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), Matchers.anyString());
        assertEquals("arn2", arn);
    }    
    
    @Test
    public void scheduleTerminatingOfIdleOutOfGracePeriod() throws Exception {
        SchedulerBackend schedulerBackend = mockBackend(
                Arrays.asList(
                    ci("id1", "arn1", true, 2000, 2000, 2000, 2000),
                    ci("id2", "arn2", true, 2000, 200, 2000, 400),
                    ci("id3", "arn3", true, 2000, 300, 2000, 300),
                    ci("id4", "arn4", true, 2000, 400, 2000, 200),
                    ci("id5", "arn5", true, 2000, 500, 2000, 100)
                    ),
                Arrays.asList(
                        ec2("id1", new Date(System.currentTimeMillis() - (CyclingECSScheduler.DEFAULT_GRACE_PERIOD.toMillis() + 1000))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);        
        String arn = scheduler.schedule("cluster", "asg", 100, 100);
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, times(1)).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), Matchers.anyString());
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
                        ec2("id1", new Date(System.currentTimeMillis() - (CyclingECSScheduler.DEFAULT_GRACE_PERIOD.toMillis() + 1000))),
                        ec2("id2", new Date()),
                        ec2("id3", new Date()),
                        ec2("id4", new Date()),
                        ec2("id5", new Date())
                ));
        CyclingECSScheduler scheduler = new CyclingECSScheduler(schedulerBackend);
        String arn = scheduler.schedule("cluster", "asg", 600, 600);
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        
        //TODO how to verify that it contained id1?
        verify(schedulerBackend, never()).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, never()).scaleTo(Matchers.anyInt(), Matchers.anyString());
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
        Future<String> arn = scheduler.scheduleImpl("cluster", "asg", 199, 399);
        Future<String> arn2 = scheduler.scheduleImpl("cluster", "asg", 599, 599);
        Thread.sleep(50); //wait to have the other thread start the processing
        scheduler.shutdownExecutor();
        scheduler.executor.awaitTermination(200, TimeUnit.MILLISECONDS); //make sure the background thread finishes
        assertEquals("arn2", arn.get());
        assertEquals("arn1", arn2.get());
        verify(schedulerBackend, never()).terminateInstances(Matchers.anyList(), Matchers.anyString());
        verify(schedulerBackend, times(1)).scaleTo(Matchers.anyInt(), Matchers.anyString());
    }
        
    
    private SchedulerBackend mockBackend(List<ContainerInstance> containerInstances, List<Instance> ec2Instances) {
        SchedulerBackend mocked = mock(SchedulerBackend.class);
        when(mocked.getClusterContainerInstances(anyString())).thenReturn(containerInstances);
        when(mocked.getInstances(Matchers.anyList())).thenReturn(ec2Instances);
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
    
}
