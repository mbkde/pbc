/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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
package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper.ExecutorQuery;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSetImpl;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.AgentQueries;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.sal.api.scheduling.PluginJob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReaperJob implements PluginJob {
    private final static Logger logger = LoggerFactory.getLogger(ReaperJob.class);

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        try {
            executeImpl(jobDataMap);
        } catch (Throwable t) {
            logger.error("Throwable catched and swallowed to preserve rescheduling of the task", t);
        }
    }

    public void executeImpl(Map<String, Object> jobDataMap) {

        AgentManager agentManager = (AgentManager) jobDataMap.get(Reaper.REAPER_AGENT_MANAGER_KEY);
        ExecutableAgentsHelper executableAgentsHelper = (ExecutableAgentsHelper) jobDataMap.get(Reaper.REAPER_AGENTS_HELPER_KEY);
        BuildQueueManager buildQueueManager = (BuildQueueManager) jobDataMap.get(Reaper.REAPER_BUILDQUEUEMANAGER_KEY);
        CachedPlanManager cachedPlanManager = (CachedPlanManager) jobDataMap.get(Reaper.REAPER_CACHEDPLANMANAGER_KEY);
        AgentRemovals agentRemovals = (AgentRemovals) jobDataMap.get(Reaper.REAPER_REMOVALS_KEY);
        
        List<BuildAgent> deathList = (List<BuildAgent>) jobDataMap.get(Reaper.REAPER_DEATH_LIST);

        // Stop and remove disabled agents
        for (BuildAgent agent : deathList) {
            agent.accept(new DeleterGraveling(agentRemovals, agentManager, buildQueueManager, cachedPlanManager));
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
            if (AgentQueries.isDockerAgent(agent) &&
                    agent.getAgentStatus().isIdle() &&
                    creationTime != null &&
                    currentTime - creationTime.getTime() > Reaper.REAPER_THRESHOLD_MILLIS) {
                relevantAgents.add(agent);
            }
        }

        // Disable enabled agents
        relevantAgents.stream().filter(BuildAgent::isEnabled).forEach(agent -> {
            agent.accept(new SleeperGraveling(agentManager));
            deathList.add(agent);
        });
        jobDataMap.put(Reaper.REAPER_DEATH_LIST, deathList);
    }
}
