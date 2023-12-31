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
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSetImpl;
import com.atlassian.buildeng.isolated.docker.AgentQueries;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.buildeng.isolated.docker.Constants;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class ReaperJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(ReaperJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            executeImpl(context.getJobDetail().getJobDataMap());
        } catch (Throwable t) {
            // this is throwable because of NoClassDefFoundError and alike.
            // These are not Exception subclasses and actually
            // throwing something here will stop rescheduling the job forever (until next redeploy)
            logger.error("Exception caught and swallowed to preserve rescheduling of the task", t);
        }
    }

    void executeImpl(Map<String, Object> jobDataMap) {

        AgentManager agentManager = (AgentManager) jobDataMap.get(Reaper.REAPER_AGENT_MANAGER_KEY);
        AgentRemovals agentRemovals = (AgentRemovals) jobDataMap.get(Reaper.REAPER_REMOVALS_KEY);
        ExecutableAgentsHelper executableAgentsHelper =
                (ExecutableAgentsHelper) jobDataMap.get(Reaper.REAPER_AGENTS_HELPER_KEY);

        RequirementSetImpl reqs = new RequirementSetImpl();
        reqs.addRequirement(new RequirementImpl(Constants.CAPABILITY_RESULT, true, ".*"));
        Collection<BuildAgent> agents =
                executableAgentsHelper.getExecutableAgents(ExecutorQuery.newQueryWithoutAssignments(reqs)
                        .withOfflineIncluded()
                        .withDisabledIncluded());

        RequirementSetImpl ephemeralReqs = new RequirementSetImpl();
        ephemeralReqs.addRequirement(new RequirementImpl(Constants.EPHEMERAL_CAPABILITY_RESULT, true, ".*"));
        agents.addAll(executableAgentsHelper.getExecutableAgents(ExecutorQuery.newQueryWithoutAssignments(ephemeralReqs)
                .withOfflineIncluded()
                .withDisabledIncluded()));

        for (BuildAgent agent : agents) {
            if (agentShouldBeKilled(agent)) {
                // we want to kill disabled docker agents
                agent.accept(new DeleterGraveling(agentRemovals));
            } else if (agentShouldBeDisabled(agent)) {
                // Stop and remove disabled agents
                agent.accept(new SleeperGraveling(agentManager));
            }
        }
    }

    // return true if the given agent should be killed, false otherwise
    private boolean agentShouldBeKilled(BuildAgent agent) {
        return !agent.isEnabled() && (AgentQueries.isDockerAgent(agent));
    }

    // return true if the given agent should be disabled, false otherwise
    private boolean agentShouldBeDisabled(BuildAgent agent) {
        // Only care about agents which are remote, idle and 'old' or offline
        if (agent.isEnabled() && (AgentQueries.isDockerAgent(agent))) {
            PipelineDefinition definition = agent.getDefinition();
            Date creationTime = definition.getCreationDate();
            long currentTime = System.currentTimeMillis();
            return (agent.getAgentStatus().isIdle()
                            && creationTime != null
                            && currentTime - creationTime.getTime() > Reaper.REAPER_THRESHOLD_MILLIS)
                    ||
                    // Ideally BuildCancelledEventListener#onOfflineAgent captures offline agents and removes them.
                    // However, the removal can fail for other reasons, e.g. bamboo is in a bad state.
                    // This condition check here works as the last defense to clean offline pbc agents
                    !agent.isActive();
        }
        return false;
    }
}
