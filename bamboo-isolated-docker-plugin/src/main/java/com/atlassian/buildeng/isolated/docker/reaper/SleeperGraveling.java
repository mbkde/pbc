package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgent.BuildAgentVisitor;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SleeperGraveling implements BuildAgentVisitor {
    private final AgentManager agentManager;

    private final Logger LOG = LoggerFactory.getLogger(SleeperGraveling.class);

    public SleeperGraveling(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    @Override
    public void visitLocal(LocalBuildAgent localBuildAgent) {
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
                // Disable the agent
                LOG.info("Disabling dangling agent {} (id: {})", agentName, agentId);
                pipelineDefinition.setEnabled(false);
                agentManager.savePipeline(pipelineDefinition, null);
            }
        });
    }
    
}
