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
package com.atlassian.buildeng.isolated.docker.lifecycle;

import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.event.BuildCanceledEvent;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.events.AgentOfflineEvent;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.reaper.DeleterGraveling;
import com.atlassian.event.api.EventListener;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * when build is cancelled on docker agents we want to remove them.
 * @author mkleint
 */
public class BuildCancelledEventListener {
    private final Logger LOG = LoggerFactory.getLogger(BuildCancelledEventListener.class);
    private final AgentManager agentManager;
    private final AgentCommandSender agentCommandSender;

    public BuildCancelledEventListener(AgentManager agentManager, AgentCommandSender agentCommandSender) {
        this.agentManager = agentManager;
        this.agentCommandSender = agentCommandSender;
    }
    
    @EventListener
    public void onCancelledBuild(BuildCanceledEvent event) {
        Long agentId = event.getAgentId();
        if (agentId != null) {
            BuildAgent agent = agentManager.getAgent(agentId);
            if (isDockerAgent(agent)) {
                LOG.info("Stopping docker agent for cancelled build {} {}:{}", event.getBuildResultKey(), agent.getName(), agentId);
                DeleterGraveling.stopAndKeepAgentRemotely(agent, agentManager, agentCommandSender);
            }
        }
    }
    
    @EventListener
    public void onOfflineAgent(AgentOfflineEvent event) {
        if (event.getBuildAgent() != null) {
            if (isDockerAgent(event.getBuildAgent())) {
                long agentId = event.getBuildAgent().getId();
                try {
                    LOG.info("Removing offline docker agent {}:{}", event.getBuildAgent().getName(), agentId);
                    agentManager.removeAgent(agentId);
                } catch (TimeoutException ex) {
                    LOG.warn("Timed out removing agent", ex);
                }
            }
        }
    }

    private boolean isDockerAgent(BuildAgent agent) {
        if (agent == null) {
            return false;
        }
        final boolean[] isDocker = new boolean[1];
        isDocker[0] = false;
        agent.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                if (pipelineDefinition.getCapabilitySet() != null) {
                    isDocker[0] = pipelineDefinition.getCapabilitySet().getCapability(Constants.CAPABILITY_RESULT) != null;
                }
            }
        });
        return isDocker[0];
    }
}
