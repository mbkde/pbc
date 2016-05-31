package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgent.BuildAgentVisitor;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import com.atlassian.bamboo.v2.build.agent.messages.StopAgentNicelyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class DeleterGraveling implements BuildAgentVisitor {
    private final AgentCommandSender agentCommandSender;
    private final AgentManager agentManager;

    private final Logger LOG = LoggerFactory.getLogger(DeleterGraveling.class);

    public DeleterGraveling(AgentCommandSender agentCommandSender, AgentManager agentManager) {
        this.agentCommandSender = agentCommandSender;
        this.agentManager = agentManager;
    }

    @Override
    public void visitLocal(LocalBuildAgent localBuildAgent) {
        // Gravelings only visit remote agents
    }

    @Override
    public void visitRemote(final BuildAgent buildAgent) {
        buildAgent.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {
                LOG.error("Wrong agent picked up. Type:{} Idle:{} Name:{}", 
                        buildAgent.getType(), buildAgent.getAgentStatus().isIdle(),
                        buildAgent.getName());
            }

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                Long agentId = buildAgent.getId();
                String agentName = buildAgent.getName();
                // Stop the agent
                try {
                    buildAgent.setRequestedToBeStopped(true); // Set status correctly
                    agentManager.removeAgent(agentId);        // Remove agent from the UI/server side
                    agentCommandSender.send(new StopAgentNicelyMessage(), agentId);
                    LOG.info("Successfully reaped agent {} (id: {})", agentName, agentId);
                } catch (TimeoutException e) {
                    LOG.error(String.format("timeout on removing agent %s (id: %s)", agentName, agentId), e);
                }
            }
        });
    }
}
