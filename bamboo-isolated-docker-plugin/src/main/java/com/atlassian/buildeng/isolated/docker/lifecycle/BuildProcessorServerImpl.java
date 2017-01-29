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

import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.CustomBuildProcessorServer;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.CurrentlyBuilding;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.isolated.docker.Constants;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the purpose of the class is to do cleanup if the normal way of killing the agent
 * after the job completion fails.
 * The one use case we know about is BUILDENG-10514 where the agent fails to run any
 * pre or post actions on the agent if artifact download fails.
 * 
 */
public class BuildProcessorServerImpl implements CustomBuildProcessorServer {
    private final Logger LOG = LoggerFactory.getLogger(BuildProcessorServerImpl.class);

    private BuildContext buildContext;
    private final AgentRemovals agentRemovals;
    private final BuildExecutionManager buildExecutionManager;

    public BuildProcessorServerImpl(AgentRemovals agentRemovals, BuildExecutionManager buildExecutionManager) {
        this.agentRemovals = agentRemovals;
        this.buildExecutionManager = buildExecutionManager;
    }
    
    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() throws Exception {
        Configuration conf = AccessConfiguration.forContext(buildContext);
        CurrentBuildResult buildResult = buildContext.getBuildResult();

        // in some cases the agent cannot kill itself (eg. when artifact subscription fails
        // and our StopDockerAgentBuildProcessor is not executed. absence of the marker property tells us that we didn't run on agent
        if (conf.isEnabled() &&  null == buildResult.getCustomBuildData().get(Constants.RESULT_AGENT_KILLED_ITSELF)) {
            CurrentlyBuilding building = buildExecutionManager.getCurrentlyBuildingByBuildResult(buildContext);
            Long agentId = null;
            if (building != null) {
                agentId = building.getBuildAgentId();
            }
            if (building != null && agentId != null) {
                agentRemovals.stopAgentRemotely(agentId);
                agentRemovals.removeAgent(agentId);
                LOG.info("Build result {} not shutting down normally, killing agent {} explicitly.", buildContext.getBuildResultKey(), agentId);
            } else {
                LOG.warn("Agent for {} not found. Cannot stop the agent.", buildContext.getBuildResultKey());
            }
            
        }
        return buildContext;
    }
    
}
