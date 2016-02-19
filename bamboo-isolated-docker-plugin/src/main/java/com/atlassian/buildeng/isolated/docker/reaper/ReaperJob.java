package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSetImpl;
import com.atlassian.sal.api.scheduling.PluginJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class ReaperJob implements PluginJob {
    private final Logger LOG = LoggerFactory.getLogger(ReaperJob.class);

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        AgentManager agentManager = (AgentManager) jobDataMap.get(Constants.REAPER_AGENT_MANAGER_KEY);
        ExecutableAgentsHelper executableAgentsHelper = (ExecutableAgentsHelper) jobDataMap.get(Constants.REAPER_AGENTS_HELPER_KEY);
        AgentCommandSender agentCommandSender = (AgentCommandSender) jobDataMap.get(Constants.REAPER_COMMAND_SENDER_KEY);

        RequirementSetImpl reqs = new RequirementSetImpl();
        reqs.addRequirement(new RequirementImpl(com.atlassian.buildeng.isolated.docker.Constants.CAPABILITY, true, ".*"));
        Collection<BuildAgent> agents = executableAgentsHelper.getExecutableAgents(ExecutableAgentsHelper.ExecutorQuery.newQuery(reqs));
        for (BuildAgent agent: agents) {
            if (agent.getType() == AgentType.REMOTE) {
                PipelineDefinition definition = agent.getDefinition();
                if (agent.getAgentStatus().isIdle() && new Date().getTime() - definition.getCreationDate().getTime() > Constants.REAPER_THRESHOLD_MILLIS) {
                    long agentId = agent.getId();
                    String agentName = agent.getName();
                    LOG.info(String.format("Reaping dangling agent %s (id: %s)", agentName, agentId));
                    try {
                        agent.accept(new Graveling(agentCommandSender, agentId)); // Kill the agent on its side
                        agent.setRequestedToBeStopped(true);                      // Set status correctly
                        agentManager.removeAgent(agentId);                        // Remove agent from the UI/server side
                        LOG.info(String.format("Successfully reaped agent %s (id: %s)", agentName, agentId));
                    } catch (TimeoutException e) {
                        LOG.error(String.format("timeout on removing agent %s (id: %s)", agentName, agentId), e);
                    }
                }
            }
        }
    }
}
