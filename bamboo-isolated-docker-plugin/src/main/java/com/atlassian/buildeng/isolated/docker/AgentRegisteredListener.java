/*
 * Copyright 2017 Atlassian Pty Ltd.
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

import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.EphemeralAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.event.agent.AgentRegisteredEvent;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.buildeng.isolated.docker.jmx.JMXAgentsService;
import com.atlassian.event.api.EventListener;

public class AgentRegisteredListener {

    private final UnmetRequirements unmetRequirements;
    private final JMXAgentsService jmx;

    public AgentRegisteredListener(UnmetRequirements unmetRequirements, JMXAgentsService jmx) {
        this.unmetRequirements = unmetRequirements;
        this.jmx = jmx;
    }

    @EventListener
    public void agentRegistered(AgentRegisteredEvent event) {
        event.getAgent().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {}

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {}

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                CapabilitySet cs = pipelineDefinition.getCapabilitySet();
                if (cs != null && cs.getCapability(Constants.CAPABILITY_RESULT) != null) {
                    jmx.incrementActive();
                }
                unmetRequirements.markAndStopTheBuild(pipelineDefinition);
            }

            @Override
            // we don't need to make any changes here as Ephemeral agents will stop running automatically
            public void visitEphemeral(EphemeralAgentDefinition pipelineDefinition) {}
        });
    }
}
