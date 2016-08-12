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
import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostJobAction;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.util.OptionalLong;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/**
 * runs on server and removes the agent from db after StopDockerAgentBuildProcessor killed it.
 */
public class PostJobActionImpl implements PostJobAction {
    private static final Logger LOG = LoggerFactory.getLogger(PostJobActionImpl.class);

    private final AgentManager agentManager;

    private PostJobActionImpl(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    @Override
    public void execute(@NotNull StageExecution stageExecution, @NotNull Job job, @NotNull BuildResultsSummary buildResultsSummary) {
        Configuration config = Configuration.forBuildResultSummary(buildResultsSummary);
        if (config.isEnabled()) {
            Long agentId = buildResultsSummary.getBuildAgentId();
            if (agentId == null) {
                //not sure why the build agent id is null sometimes. but because it is,
                //our offline remote agents keep accumulating
                OptionalLong found = agentManager.getAllRemoteAgents().stream()
                        .filter((BuildAgent t) -> {
                          return isDockerAgentForResult(t, buildResultsSummary.getPlanResultKey());
                        })
                        .mapToLong((BuildAgent value) -> value.getId()).findFirst();
                if (found.isPresent()) {
                    LOG.info("Found missing build agent for job " + job.getId());
                    agentId = found.getAsLong();
                } else {
                    LOG.info("Unable to find build agent for job " + job.getId());
                    return;
                }
            }
            try {
                agentManager.removeAgent(agentId);
            } catch (TimeoutException ex) {
                LOG.error("timeout on removing agent " + buildResultsSummary.getBuildAgentId(), ex);
            }
        }
    }

    private boolean isDockerAgentForResult(BuildAgent t, PlanResultKey key) {
        final boolean[] b = new boolean[1];
        b[0] = false;
        t.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {
            }
            
            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {
            }
            
            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                final CapabilitySet capabilitySet = pipelineDefinition.getCapabilitySet();
                if (capabilitySet != null) {
                    Capability cap = capabilitySet.getCapability(Constants.CAPABILITY_RESULT);
                    if (cap != null) {
                        if (key.equals(PlanKeys.getPlanResultKey(cap.getValue()))) {
                            b[0] = true;
                        }
                    }
                }
            }
        });
        return b[0];
    }

}
