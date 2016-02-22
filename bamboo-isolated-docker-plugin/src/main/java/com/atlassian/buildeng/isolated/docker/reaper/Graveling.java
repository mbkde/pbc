package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import com.atlassian.bamboo.v2.build.agent.messages.StopAgentNicelyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class Graveling implements BuildAgent.BuildAgentVisitor {
    private final AgentCommandSender agentCommandSender;
    private final AgentManager agentManager;
    private final boolean isSleeper;
    private final Logger LOG = LoggerFactory.getLogger(Graveling.class);


    private Graveling(AgentCommandSender agentCommandSender, AgentManager agentManager,boolean isSleeper) {
        this.agentCommandSender = agentCommandSender;
        this.agentManager = agentManager;
        this.isSleeper = isSleeper;
    }

    public static Graveling sleeper(AgentManager agentManager) {
        return new Graveling(null, agentManager, true);
    }

    public static Graveling deleter(AgentCommandSender agentCommandSender, AgentManager agentManager) {
        return new Graveling(agentCommandSender, agentManager, false);
    }

    @Override
    public void visitLocal(LocalBuildAgent localBuildAgent) {
        // Gravelings only visit remote agents
    }

    @Override
    public void visitRemote(BuildAgent buildAgent) {
        Long agentId = buildAgent.getId();
        String agentName = buildAgent.getName();
        if (isSleeper) {
            // Disable the agent
            LOG.info(String.format("Disabling dangling agent %s (id: %s)", agentName, agentId));
            PipelineDefinition pipelineDefinition = buildAgent.getDefinition();
            pipelineDefinition.setEnabled(false);
            agentManager.savePipeline(pipelineDefinition, null);
        } else {
            // Stop the agent
            try {
                buildAgent.setRequestedToBeStopped(true); // Set status correctly
                agentManager.removeAgent(agentId);        // Remove agent from the UI/server side
                agentCommandSender.send(new StopAgentNicelyMessage(), agentId);
                LOG.info(String.format("Successfully reaped agent %s (id: %s)", agentName, agentId));
            } catch (TimeoutException e) {
                LOG.error(String.format("timeout on removing agent %s (id: %s)", agentName, agentId), e);
            }
        }
    }
}
