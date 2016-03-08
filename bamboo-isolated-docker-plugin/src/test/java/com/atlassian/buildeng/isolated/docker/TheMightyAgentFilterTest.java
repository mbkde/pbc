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
package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.RemoteAgentDefinitionImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySetImpl;
import com.atlassian.bamboo.v2.build.agent.capability.MinimalRequirementSet;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSetImpl;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * @author mkleint
 */
public class TheMightyAgentFilterTest {
    
    /**
     * Test of filter method, of class TheMightyAgentFilter.
     */
    @Test
    public void testDockerJob() {
        CommonContext context = mock(CommonContext.class);
        when(context.getResultKey()).thenReturn(PlanKeys.getPlanResultKey("AAA-BBB-JOB", 1));
        Collection<BuildAgent> agents = mockAgents();
        MinimalRequirementSet requirements = mock(MinimalRequirementSet.class);
        Set<Requirement> req = new HashSet<>();
        req.add(new RequirementImpl(Constants.CAPABILITY, true, "image"));
        when(requirements.getRequirements()).thenReturn(req);
        
        TheMightyAgentFilter instance = new TheMightyAgentFilter();
        Collection<BuildAgent> result = instance.filter(context, agents, requirements);
        assertEquals(1, result.size());
        
    }
    
    @Test
    public void testDockerJobMissignAgent() {
        CommonContext context = mock(CommonContext.class);
        when(context.getResultKey()).thenReturn(PlanKeys.getPlanResultKey("AAA-BBB-JOB", 2));
        Collection<BuildAgent> agents = mockAgents();
        MinimalRequirementSet requirements = mock(MinimalRequirementSet.class);
        Set<Requirement> req = new HashSet<>();
        req.add(new RequirementImpl(Constants.CAPABILITY, true, "image"));
        when(requirements.getRequirements()).thenReturn(req);
        
        TheMightyAgentFilter instance = new TheMightyAgentFilter();
        Collection<BuildAgent> result = instance.filter(context, agents, requirements);
        assertEquals(0, result.size());
        
    }    

    @Test
    public void testNonDockerJob() {
        CommonContext context = mock(CommonContext.class);
        when(context.getResultKey()).thenReturn(PlanKeys.getPlanResultKey("AAA-BBB-JOB", 1));
        Collection<BuildAgent> agents = mockAgents();
        MinimalRequirementSet requirements = mock(MinimalRequirementSet.class);
        Set<Requirement> req = new HashSet<>();
        when(requirements.getRequirements()).thenReturn(req);
        
        TheMightyAgentFilter instance = new TheMightyAgentFilter();
        Collection<BuildAgent> result = instance.filter(context, agents, requirements);
        assertEquals(3, result.size()); //local and elastic and 1 remote
        
    }
    
    
    private Collection<BuildAgent> mockAgents() {
        CapabilitySet cs1 = new CapabilitySetImpl();
        cs1.addCapability(new CapabilityImpl(Constants.CAPABILITY_RESULT, "AAA-BBB-JOB-1"));
        cs1.addCapability(new CapabilityImpl(Constants.CAPABILITY, "true"));
        CapabilitySet cs2 = new CapabilitySetImpl();
        cs2.addCapability(new CapabilityImpl(Constants.CAPABILITY_RESULT, "AAA-BBB-JOB2-2"));
        cs2.addCapability(new CapabilityImpl(Constants.CAPABILITY, "true"));
        Collection<BuildAgent> agents = Arrays.asList(new BuildAgent[] {
            mockLocalAgent(),
            mockElasticAgent(),
            mockRemoteAgent(cs1),
            mockRemoteAgent(cs2),
            mockRemoteAgent(new CapabilitySetImpl())
        });
        return agents;
    }
    
    
    private BuildAgent mockAgent(AgentType type) {
        BuildAgent toRet = mock(BuildAgent.class);
        when(toRet.getType()).thenReturn(type);
        return toRet;
    }
    
    BuildAgent mockLocalAgent() {
        return mockAgent(AgentType.LOCAL);
    }
    
    BuildAgent mockElasticAgent() {
        return mockAgent(AgentType.ELASTIC);
    }
    
    BuildAgent mockRemoteAgent(CapabilitySet set) {
        BuildAgent agent = mockAgent(AgentType.REMOTE);
        RemoteAgentDefinition d2 = new RemoteAgentDefinitionImpl();
        d2.setCapabilitySet(set);
        when(agent.getDefinition()).thenReturn(d2);
        return agent;
    }
    
}
