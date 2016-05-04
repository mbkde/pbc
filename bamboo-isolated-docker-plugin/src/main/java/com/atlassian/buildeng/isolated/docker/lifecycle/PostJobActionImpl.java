/*
 * Copyright 2016 Atlassian.
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

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostJobAction;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.buildeng.isolated.docker.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/**
 * runs on server and removes the agent from db after StopDockerAgentBuildProcessor killed it.
 */
class PostJobActionImpl implements PostJobAction {
    private static final Logger LOG = LoggerFactory.getLogger(PostJobActionImpl.class);

    private final AgentManager agentManager;

    private PostJobActionImpl(AgentManager agentManager) {
        this.agentManager = agentManager;
    }


    @Override
    public void execute(@NotNull StageExecution stageExecution, @NotNull Job job, @NotNull BuildResultsSummary buildResultsSummary) {
        Configuration config = Configuration.forBuildResultSummary(buildResultsSummary);
        if (config.isEnabled()) {
            if (buildResultsSummary.getBuildAgentId() == null) {
                LOG.info("Build agent already removed for job " + job.getId());
            } else try {
                agentManager.removeAgent(buildResultsSummary.getBuildAgentId());
            } catch (TimeoutException ex) {
                LOG.error("timeout on removing agent " + buildResultsSummary.getBuildAgentId(), ex);
            }
        }
    }

}
