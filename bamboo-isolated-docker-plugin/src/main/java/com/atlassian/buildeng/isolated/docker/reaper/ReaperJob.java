package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper.ExecutorQuery;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSetImpl;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.sal.api.scheduling.PluginJob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

class ReaperJob implements PluginJob {

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        AgentManager agentManager = (AgentManager) jobDataMap.get(Constants.REAPER_AGENT_MANAGER_KEY);
        ExecutableAgentsHelper executableAgentsHelper = (ExecutableAgentsHelper) jobDataMap.get(Constants.REAPER_AGENTS_HELPER_KEY);
        AgentCommandSender agentCommandSender = (AgentCommandSender) jobDataMap.get(Constants.REAPER_COMMAND_SENDER_KEY);
        List<BuildAgent> deathList = (List<BuildAgent>) jobDataMap.get(Constants.REAPER_DEATH_LIST);

        // Stop and remove disabled agents
        for (BuildAgent agent : deathList) {
            agent.accept(new DeleterGraveling(agentCommandSender, agentManager));
        }

        deathList.clear();

        RequirementSetImpl reqs = new RequirementSetImpl();
        reqs.addRequirement(new RequirementImpl(Constants.CAPABILITY, true, ".*"));
        Collection<BuildAgent> agents = executableAgentsHelper.getExecutableAgents(ExecutorQuery.newQuery(reqs));
        Collection<BuildAgent> relevantAgents = new ArrayList<>();

        // Only care about agents which are remote, idle and 'old'
        for (BuildAgent agent: agents) {
            PipelineDefinition definition = agent.getDefinition();
            Date creationTime = definition.getCreationDate();
            long currentTime = System.currentTimeMillis();
            if (agent.getType() == AgentType.REMOTE &&
                    agent.getAgentStatus().isIdle() &&
                    creationTime != null &&
                    currentTime - creationTime.getTime() > Constants.REAPER_THRESHOLD_MILLIS) {
                relevantAgents.add(agent);
            }
        }

        // Disable enabled agents
        relevantAgents.stream().filter(BuildAgent::isEnabled).forEach(agent -> {
            agent.accept(new SleeperGraveling(agentManager));
            deathList.add(agent);
        });
        jobDataMap.put(Constants.REAPER_DEATH_LIST, deathList);
    }
}
