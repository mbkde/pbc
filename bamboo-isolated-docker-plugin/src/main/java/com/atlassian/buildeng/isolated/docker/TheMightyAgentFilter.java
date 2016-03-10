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
import com.atlassian.bamboo.capability.CapabilitySetProvider;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgentRequirementFilter;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.MinimalRequirementSet;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * this beast requires restart on each deployment.
 * @author mkleint
 */
public final class TheMightyAgentFilter implements BuildAgentRequirementFilter {

    @Override
    public Collection<BuildAgent> filter(CommonContext context, Collection<BuildAgent> agents, MinimalRequirementSet requirements) {
        if (hasIsolatedDockerRequirement(requirements)) {
            for (BuildAgent agent : agents) {
                if (!AgentType.REMOTE.equals(agent.getType())) {
                    continue;
                }
                final CapabilitySet capabilitySet = CapabilitySetProvider.getAgentCapabilitySet(agent); //could this be slow??
                if (capabilitySet != null) {
                    Capability cap = capabilitySet.getCapability(Constants.CAPABILITY_RESULT);
                    if (cap != null) {
                        String resultKey = cap.getValue();
                        if (context.getResultKey().toString().equals(resultKey)) {
                            return Collections.singletonList(agent);
                        }
                    }
                }
            }
            return Collections.emptyList();
        } else {
            //make sure the isolated docker agent never picks up non-dockerized job
            ArrayList<BuildAgent> toRet = new ArrayList<>(agents.size());
            for (BuildAgent agent : agents) {
                if (AgentType.REMOTE.equals(agent.getType())) {
                    //only check remote agents
                    final CapabilitySet capabilitySet = CapabilitySetProvider.getAgentCapabilitySet(agent); //could this be slow??
                    if (capabilitySet != null) {
                        if (capabilitySet.getCapability(Constants.CAPABILITY) != null) {
                            continue;
                        }
                    }
                } 
                toRet.add(agent);
            } 
            return toRet;
        }
    }

    private static boolean hasIsolatedDockerRequirement(MinimalRequirementSet requirements) {
        return requirements.getRequirements().stream()
                .filter((Requirement t) -> Constants.CAPABILITY.equals(t.getKey()))
                .findAny()
                .isPresent();
    }
    
}
