package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import com.atlassian.bamboo.v2.build.agent.messages.StopAgentNicelyMessage;

public class Graveling implements BuildAgent.BuildAgentVisitor {
    private final AgentCommandSender agentCommandSender;
    private final long agentId;

    public Graveling(AgentCommandSender agentCommandSender, long agentId) {
        this.agentCommandSender = agentCommandSender;
        this.agentId = agentId;
    }

    @Override
    public void visitLocal(LocalBuildAgent localBuildAgent) {
        // Gravelings only kill remote agents
    }

    @Override
    public void visitRemote(BuildAgent buildAgent) {
        agentCommandSender.send(new StopAgentNicelyMessage(), agentId);
    }
}
