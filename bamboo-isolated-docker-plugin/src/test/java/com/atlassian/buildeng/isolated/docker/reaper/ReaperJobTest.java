/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.v2.build.agent.AgentStatus;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.ElasticAgentDefinitionImpl;
import com.atlassian.bamboo.v2.build.agent.RemoteAgentDefinitionImpl;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import static com.atlassian.buildeng.isolated.docker.reaper.Reaper.REAPER_AGENT_MANAGER_KEY;
import static com.atlassian.buildeng.isolated.docker.reaper.Reaper.REAPER_REMOVALS_KEY;
import static com.atlassian.buildeng.isolated.docker.reaper.Reaper.REAPER_AGENTS_HELPER_KEY;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.quartz.JobBuilder.newJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ReaperJobTest {
    ExecutableAgentsHelper executableAgentsHelper;

    JobExecutionContext context;

    private ReaperJob reaperJob;

    @Before
    public void setUp() {
        JobDataMap jobDataMap = new JobDataMap();

        AgentManager agentManager = mock(AgentManager.class);
        AgentRemovals agentRemovals = mock(AgentRemovals.class);
        executableAgentsHelper = mock(ExecutableAgentsHelper.class);

        jobDataMap.put(REAPER_AGENT_MANAGER_KEY, agentManager);
        jobDataMap.put(REAPER_REMOVALS_KEY, agentRemovals);
        jobDataMap.put(REAPER_AGENTS_HELPER_KEY, executableAgentsHelper);

        context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);

        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);

        reaperJob = new ReaperJob();
    }

    @Test
    public void jobDataShouldPersist() {
        JobDetail reaperJobDetail = newJob(ReaperJob.class)
                .build();

        assertTrue(reaperJobDetail.isPersistJobDataAfterExecution());
    }

    @Test
    public void disabledAgentsShouldBeKilled() throws JobExecutionException {
        BuildAgent agent = mockDockerAgent(minutesAgo(0));
        when(agent.isEnabled()).thenReturn(false);
        ArrayList<BuildAgent> agents = new ArrayList<>(Collections.singletonList(agent));
        when(executableAgentsHelper.getExecutableAgents(any())).thenReturn(agents);

        reaperJob.execute(context);

        verify(agent, times(1)).accept(any(DeleterGraveling.class));
        verify(agent, times(0)).accept(any(SleeperGraveling.class));
    }

    @Test
    public void disabledAgentShouldNotBeKilledIfItIsNotADockerAgent() throws JobExecutionException {
        BuildAgent agent = mockElasticAgent(minutesAgo(100));
        when(agent.isEnabled()).thenReturn(false);
        ArrayList<BuildAgent> agents = new ArrayList<>(Collections.singletonList(agent));
        when(executableAgentsHelper.getExecutableAgents(any())).thenReturn(agents);

        reaperJob.execute(context);

        verify(agent, times(0)).accept(any(DeleterGraveling.class));
        verify(agent, times(0)).accept(any(SleeperGraveling.class));
    }

    @Test
    public void idleAgentShouldBeDisabledIfCreationDateLongAgo() throws JobExecutionException {
        BuildAgent agent = mockIdleDockerAgent(minutesAgo(1000));
        when(agent.isEnabled()).thenReturn(true);
        when(agent.isActive()).thenReturn(true);
        ArrayList<BuildAgent> agents = new ArrayList<>(Collections.singletonList(agent));
        when(executableAgentsHelper.getExecutableAgents(any())).thenReturn(agents);

        reaperJob.execute(context);

        verify(agent, times(0)).accept(any(DeleterGraveling.class));
        verify(agent, times(1)).accept(any(SleeperGraveling.class));
    }

    @Test
    public void idleAgentShouldNotBeDisabledIfCreationDateTooRecent() throws JobExecutionException {
        BuildAgent agent = mockIdleDockerAgent(minutesAgo(0));
        when(agent.isEnabled()).thenReturn(true);
        when(agent.isActive()).thenReturn(true);
        ArrayList<BuildAgent> agents = new ArrayList<>(Collections.singletonList(agent));
        when(executableAgentsHelper.getExecutableAgents(any())).thenReturn(agents);

        reaperJob.execute(context);

        verify(agent, times(0)).accept(any(DeleterGraveling.class));
        verify(agent, times(0)).accept(any(SleeperGraveling.class));
    }

    @Test
    public void agentShouldNotBeDisabledIfItIsNotADockerAgent() throws JobExecutionException {
        BuildAgent agent = mockIdleElasticAgent(minutesAgo(100));
        when(agent.isEnabled()).thenReturn(true);
        when(agent.isActive()).thenReturn(true);
        ArrayList<BuildAgent> agents = new ArrayList<>(Collections.singletonList(agent));
        when(executableAgentsHelper.getExecutableAgents(any())).thenReturn(agents);

        reaperJob.execute(context);

        verify(agent, times(0)).accept(any(DeleterGraveling.class));
        verify(agent, times(0)).accept(any(SleeperGraveling.class));
    }

    private BuildAgent mockElasticAgent(Date creationDate) {
        BuildAgent agent = mock(BuildAgent.class);
        when(agent.getType()).thenReturn(AgentType.REMOTE);
        ElasticAgentDefinitionImpl agentDefinition = new ElasticAgentDefinitionImpl();
        agentDefinition.setCreationDate(creationDate);
        when(agent.getDefinition()).thenReturn(agentDefinition);
        return agent;
    }

    private BuildAgent mockDockerAgent(Date creationDate) {
        BuildAgent agent = mock(BuildAgent.class);
        RemoteAgentDefinition remoteAgentDefinition = remoteAgentDefinition();
        remoteAgentDefinition.setCreationDate(creationDate);
        when(agent.getType()).thenReturn(AgentType.REMOTE);
        when(agent.getDefinition()).thenReturn(remoteAgentDefinition);
        return agent;
    }

    private BuildAgent mockIdleDockerAgent(Date creationDate) {
        BuildAgent idleAgent = mockDockerAgent(creationDate);
        AgentStatus status = idleStatus();
        when(idleAgent.getAgentStatus()).thenReturn(status);
        return idleAgent;
    }

    private BuildAgent mockIdleElasticAgent(Date creationDate) {
        BuildAgent agent = mockElasticAgent(creationDate);
        AgentStatus status = idleStatus();
        when(agent.getAgentStatus()).thenReturn(status);
        return agent;
    }

    private AgentStatus idleStatus() {
        AgentStatus status = mock(AgentStatus.class);
        when(status.isIdle()).thenReturn(true);
        return status;
    }

    private RemoteAgentDefinition remoteAgentDefinition() {
        RemoteAgentDefinitionImpl remoteAgentDefinition = new RemoteAgentDefinitionImpl(1, "myName");
        CapabilitySet capabilitySet = mock(CapabilitySet.class);
        Capability capability = mock(Capability.class);
        when(capability.getValue()).thenReturn("value");
        when(capabilitySet.getCapability(any())).thenReturn(capability);
        remoteAgentDefinition.setCapabilitySet(capabilitySet);
        return remoteAgentDefinition;
    }

    private Date minutesAgo(int minutes) {
        return new Date(new Date().getTime() - ((long) minutes * 60 * 1000));
    }
}
