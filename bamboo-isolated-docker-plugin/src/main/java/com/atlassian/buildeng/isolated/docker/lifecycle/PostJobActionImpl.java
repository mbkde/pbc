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

package com.atlassian.buildeng.isolated.docker.lifecycle;

import com.atlassian.bamboo.Key;
import com.atlassian.buildeng.isolated.docker.AgentQueries;
import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostJobAction;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.isolated.docker.Constants;
import static com.atlassian.buildeng.isolated.docker.lifecycle.ReserveFutureCapacityPreJobAction.stagePBCExecutions;
import static com.atlassian.buildeng.isolated.docker.lifecycle.ReserveFutureCapacityPreJobAction.stagePBCJobResultKeys;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

/**
 * runs on server and removes the agent from db after StopDockerAgentBuildProcessor killed it.
 */
public class PostJobActionImpl implements PostJobAction {
    private static final Logger LOG = LoggerFactory.getLogger(PostJobActionImpl.class);
    //key in customBuildData to store the build key that is only available in CommonContexts
    static final String PBCBUILD_KEY = "pbc.buildKey";

    private final AgentRemovals agentRemovals;
    private final AgentManager agentManager;
    private final IsolatedAgentService isoService;

    private PostJobActionImpl(AgentRemovals agentRemovals, AgentManager agentManager, IsolatedAgentService isoService) {
        this.agentRemovals = agentRemovals;
        this.agentManager = agentManager;
        this.isoService = isoService;
    }

    @Override
    public void execute(@NotNull StageExecution stageExecution, @NotNull Job job, @NotNull BuildResultsSummary buildResultsSummary) {
        //cleanup future reservations in case of failure.
        if (buildResultsSummary.isFailed()) {
            Long nextStageMem = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1).collect(Collectors.summingLong((Configuration value) -> value.getMemoryTotal()));
            Long nextStageCpu = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1).collect(Collectors.summingLong((Configuration value) -> value.getCPUTotal()));
            if (nextStageCpu > 0 || nextStageMem > 0) {
                LOG.info("Resetting reservation " + buildResultsSummary.getCustomBuildData().get(PBCBUILD_KEY) +  " due to failed build result " + buildResultsSummary.getPlanResultKey());
                isoService.reserveCapacity(new BuildKeyReplica(buildResultsSummary.getCustomBuildData().get(PBCBUILD_KEY)),
                        stagePBCJobResultKeys(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1),
                        0, 0);
            }
        }
        Configuration config = AccessConfiguration.forBuildResultSummary(buildResultsSummary);
        if (config.isEnabled()) {
            String properStopped = buildResultsSummary.getCustomBuildData().get(Constants.RESULT_AGENT_KILLED_ITSELF);
            //only remove the agent when the agent was stopped from inside by StopDockerAgentBuildProcessor.
            if (StringUtils.equals("true", properStopped)) {
                BuildAgent agent = findAgent(job, buildResultsSummary);
                if (agent != null) {
                    agentRemovals.removeAgent(agent);
                }
            }
        }
    }

    private BuildAgent findAgent(Job job, BuildResultsSummary buildResultsSummary) {
        Long agentId = buildResultsSummary.getBuildAgentId();
        if (agentId == null) {
            //not sure why the build agent id is null sometimes. but because it is,
            //our offline remote agents keep accumulating
            Optional<BuildAgent> found = agentManager.getAllRemoteAgents().stream()
                    .filter((BuildAgent t) -> {
                        return AgentQueries.isDockerAgentForResult(t, buildResultsSummary.getPlanResultKey());
                    })
                    .findFirst();
            if (found.isPresent()) {
                LOG.info("Found missing build agent for job " + job.getId());
                return found.get();
            } else {
                LOG.error("Unable to find build agent for job " + job.getId());
                return null;
            }
        } else {
            BuildAgent test = agentManager.getAgent(agentId);
            //BUILDENG-12398 very rare not exactly sure how these can happen
            // log when that happens again to know for sure.
            if (test == null) {
                // on a rerun is the buildResultSummary still holding the old agentId sometimes?
                LOG.error("Agent {} for job {} referenced from buildResultSummary but missing in db.", agentId, job.getId());
                return null;
            } else if (!AgentQueries.isDockerAgent(test)) {
                //could it be an elastic/remote agent that was running the job while the plan was changed?
                LOG.error("Agent {} for job {} referenced from buildResultSummary wa not PBC agent", agentId, job.getId());
                return null;
            }
            return test;
        }
    }

    //because BuildKey in bamboo can't be recreated from string and we need to store it
    // in string, string map and recreate
    private static class BuildKeyReplica implements Key {

        private final String key;

        public BuildKeyReplica(String key) {
            this.key = key;
        }


        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String toString() {
            return getKey();
        }

    }
}
