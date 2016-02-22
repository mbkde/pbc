package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSetImpl;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.sal.api.scheduling.PluginJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class ReaperJob implements PluginJob {

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        AgentManager agentManager = (AgentManager) jobDataMap.get(Constants.REAPER_AGENT_MANAGER_KEY);
        ExecutableAgentsHelper executableAgentsHelper = (ExecutableAgentsHelper) jobDataMap.get(Constants.REAPER_AGENTS_HELPER_KEY);
        AgentCommandSender agentCommandSender = (AgentCommandSender) jobDataMap.get(Constants.REAPER_COMMAND_SENDER_KEY);

        RequirementSetImpl reqs = new RequirementSetImpl();
        reqs.addRequirement(new RequirementImpl(com.atlassian.buildeng.isolated.docker.Constants.CAPABILITY, true, ".*"));
        Collection<BuildAgent> agents = executableAgentsHelper.getExecutableAgents(ExecutableAgentsHelper.ExecutorQuery.newQuery(reqs));
        Collection<BuildAgent> relevantAgents = new ArrayList<>();

        // Only care about agents which are remote, idle and 'old'
        for (BuildAgent agent: agents) {
            PipelineDefinition definition = agent.getDefinition();
            if (agent.getType() == AgentType.REMOTE &&
                    agent.getAgentStatus().isIdle() &&
                    new Date().getTime() - definition.getCreationDate().getTime() > Constants.REAPER_THRESHOLD_MILLIS) {
                relevantAgents.add(agent);
            }
        }

        for (BuildAgent agent: relevantAgents) {
            // Disable enabled agents
            if (agent.isEnabled()) {
                agent.accept(Graveling.sleeper(agentManager));
            // Stop and remove disabled agents
            } else {
                agent.accept(Graveling.deleter(agentCommandSender, agentManager));
            }
        }
    }
}
