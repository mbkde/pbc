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
import com.atlassian.bamboo.capability.CapabilitySetProvider;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgentRequirementFilter;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.MinimalRequirementSet;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this beast requires restart on each deployment.
 *
 * @author mkleint
 */
public final class TheMightyAgentFilter implements BuildAgentRequirementFilter {

    private static final Logger log = LoggerFactory.getLogger(TheMightyAgentFilter.class);

    @Override
    public Collection<BuildAgent> filter(
            CommonContext context, Collection<BuildAgent> agents, MinimalRequirementSet requirements) {
        log.debug("have {} agents for {}", agents.size(), context.getResultKey());
        if (isPBCContext(context)) {
            for (BuildAgent agent : agents) {
                if (!AgentType.REMOTE.equals(agent.getType())) {
                    continue;
                }
                CapabilitySet capabilitySet =
                        CapabilitySetProvider.getAgentCapabilitySet(agent); // could this be slow??
                if (capabilitySet != null) {
                    Capability cap = capabilitySet.getCapability(Constants.CAPABILITY_RESULT);
                    if (cap != null) {
                        String resultKey = cap.getValue();
                        if (context.getResultKey().getKey().equals(resultKey)) {
                            log.debug("returned agent: {} id={}", agent.getName(), agent.getId());
                            return Collections.singletonList(agent);
                        }
                    }
                }
            }
            return Collections.emptyList();
        } else {
            // make sure the isolated docker agent never picks up non-dockerized job
            ArrayList<BuildAgent> toRet = new ArrayList<>(agents.size());
            for (BuildAgent agent : agents) {
                if (AgentType.REMOTE.equals(agent.getType())) {
                    // only check remote agents
                    CapabilitySet capabilitySet = CapabilitySetProvider.getAgentCapabilitySet(agent);
                    if (capabilitySet != null) {
                        if (capabilitySet.getCapability(Constants.CAPABILITY_RESULT) != null) {
                            continue;
                        }
                    }
                }
                toRet.add(agent);
            }
            return toRet;
        }
    }

    private static boolean isPBCContext(CommonContext context) {
        return AccessConfiguration.forContext(context).isEnabled();
    }
}
