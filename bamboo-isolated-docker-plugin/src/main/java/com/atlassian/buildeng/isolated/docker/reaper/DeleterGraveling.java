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

import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgent.BuildAgentVisitor;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DeleterGraveling implements BuildAgentVisitor {
    private final AgentRemovals agentRemovals;

    private static final Logger LOG = LoggerFactory.getLogger(DeleterGraveling.class);

    public DeleterGraveling(AgentRemovals agentRemovals) {
        this.agentRemovals = agentRemovals;
    }

    @Override
    public void visitLocal(LocalBuildAgent localBuildAgent) {
        // Gravelings only visit remote agents
    }

    @Override
    public void visitRemote(final BuildAgent buildAgent) {
        buildAgent.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {
                LOG.error("Wrong agent picked up. Type:{} Idle:{} Name:{}",
                        buildAgent.getType(),
                        buildAgent.getAgentStatus().isIdle(),
                        buildAgent.getName());
            }

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                agentRemovals.stopAgentRemotely(buildAgent);
                agentRemovals.removeAgent(buildAgent);
            }
        });
    }


}
