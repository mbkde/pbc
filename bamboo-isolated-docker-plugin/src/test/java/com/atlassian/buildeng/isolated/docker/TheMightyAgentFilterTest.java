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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.RemoteAgentDefinitionImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySetImpl;
import com.atlassian.bamboo.v2.build.agent.capability.MinimalRequirementSet;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TheMightyAgentFilterTest {
    
    PlanResultKey resultKey1 = PlanKeys.getPlanResultKey("AAA-BBB-JOB", 1);
    PlanResultKey resultKey2 = PlanKeys.getPlanResultKey("AAA-BBB-JOB2", 2);
    
    /**
     * Test of filter method, of class TheMightyAgentFilter.
     */
    @Test
    public void testDockerJob() {
        HashMap<String, String> customConfig = new HashMap<>();
        BuildContext context = mockBuildContext(customConfig);
        when(context.getResultKey()).thenReturn(resultKey1);
        CurrentResult currResult = mock(CurrentResult.class);
        when(currResult.getCustomBuildData()).thenReturn(customConfig);
        ConfigurationBuilder.create("aaa").build().copyToResult(currResult, new DefaultContainerSizeDescriptor());
        Collection<BuildAgent> agents = mockAgents();
        
        TheMightyAgentFilter instance = new TheMightyAgentFilter();
        Collection<BuildAgent> result = instance.filter(context, agents, mock(MinimalRequirementSet.class));
        assertEquals(1, result.size());
        
    }
    
    @Test
    public void testDockerJobMissingAgent() {
        HashMap<String, String> customConfig = new HashMap<>();
        CurrentResult currResult = mock(CurrentResult.class);
        when(currResult.getCustomBuildData()).thenReturn(customConfig);
        ConfigurationBuilder.create("aaa").build().copyToResult(currResult, new DefaultContainerSizeDescriptor());
        BuildContext context = mockBuildContext(customConfig);
        when(context.getResultKey()).thenReturn(PlanKeys.getPlanResultKey("AAA-BBB-JOB", 3));
        Collection<BuildAgent> agents = mockAgents();
        
        TheMightyAgentFilter instance = new TheMightyAgentFilter();
        Collection<BuildAgent> result = instance.filter(context, agents, mock(MinimalRequirementSet.class));
        assertEquals(0, result.size());
        
    }    

    @Test
    public void testNonDockerJob() {
        HashMap<String, String> customConfig = new HashMap<>();
        BuildContext context = mockBuildContext(customConfig);
        when(context.getResultKey()).thenReturn(PlanKeys.getPlanResultKey("AAA-BBB-JOB", 1));
        Collection<BuildAgent> agents = mockAgents();
        MinimalRequirementSet requirements = mock(MinimalRequirementSet.class);
        
        TheMightyAgentFilter instance = new TheMightyAgentFilter();
        Collection<BuildAgent> result = instance.filter(context, agents, requirements);
        assertEquals(3, result.size()); //local and elastic and 1 remote
        
    }
    
    
    private Collection<BuildAgent> mockAgents() {
        CapabilitySet cs1 = new CapabilitySetImpl();
        cs1.addCapability(new CapabilityImpl(Constants.CAPABILITY_RESULT, resultKey1.getKey()));
        CapabilitySet cs2 = new CapabilitySetImpl();
        cs2.addCapability(new CapabilityImpl(Constants.CAPABILITY_RESULT, resultKey2.getKey()));
        return Arrays.asList(
                mockLocalAgent(),
                mockElasticAgent(),
                mockRemoteAgent(cs1),
                mockRemoteAgent(cs2),
                mockRemoteAgent(new CapabilitySetImpl())
        );
    }
    
    
    private BuildAgent mockAgent(AgentType type) {
        BuildAgent toRet = mock(BuildAgent.class);
        Mockito.lenient().when(toRet.getType()).thenReturn(type);
        return toRet;
    }
    
    private BuildAgent mockLocalAgent() {
        return mockAgent(AgentType.LOCAL);
    }
    
    private BuildAgent mockElasticAgent() {
        return mockAgent(AgentType.ELASTIC);
    }
    
    private BuildAgent mockRemoteAgent(CapabilitySet set) {
        BuildAgent agent = mockAgent(AgentType.REMOTE);
        RemoteAgentDefinition d2 = new RemoteAgentDefinitionImpl();
        d2.setCapabilitySet(set);
        Mockito.lenient().when(agent.getDefinition()).thenReturn(d2);
        return agent;
    }

    private BuildContext mockBuildContext(HashMap<String, String> customConfig) {
        BuildContext context = mock(BuildContext.class);
        BuildDefinition bd = mock(BuildDefinition.class);
        when(context.getBuildDefinition()).thenReturn(bd);
        when(bd.getCustomConfiguration()).thenReturn(customConfig);
        return context;
    }
    
}
