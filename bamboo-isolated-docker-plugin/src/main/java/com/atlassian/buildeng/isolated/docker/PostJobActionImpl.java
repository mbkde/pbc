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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostJobAction;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import java.util.concurrent.TimeoutException;
import org.slf4j.LoggerFactory;

public class PostJobActionImpl implements PostJobAction {
    private final org.slf4j.Logger LOG = LoggerFactory.getLogger(PostJobActionImpl.class);

    private final AgentManager agentManager;

    public PostJobActionImpl(AgentManager agentManager) {
        this.agentManager = agentManager;
    }


    @Override
    public void execute(StageExecution stageExecution, Job job, BuildResultsSummary buildResultsSummary) {
        Configuration config = Configuration.forJob(job);
        if (config.isEnabled()) {
            try {
                agentManager.removeAgent(buildResultsSummary.getBuildAgentId());
            } catch (TimeoutException ex) {
                LOG.error("timeout on removing agent " + buildResultsSummary.getBuildAgentId(), ex);
            }
        }
    }

}
