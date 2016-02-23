package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SleeperGraveling implements BuildAgent.BuildAgentVisitor {
    private final AgentManager agentManager;

    private final Logger LOG = LoggerFactory.getLogger(SleeperGraveling.class);

    public SleeperGraveling(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    @Override
    public void visitLocal(LocalBuildAgent localBuildAgent) {
        // Gravelings only visit remote agents
    }

    @Override
    public void visitRemote(BuildAgent buildAgent) {
        Long agentId = buildAgent.getId();
        String agentName = buildAgent.getName();
        // Disable the agent
        LOG.info(String.format("Disabling dangling agent %s (id: %s)", agentName, agentId));
        PipelineDefinition pipelineDefinition = buildAgent.getDefinition();
        pipelineDefinition.setEnabled(false);
        agentManager.savePipeline(pipelineDefinition, null);

    }
}
