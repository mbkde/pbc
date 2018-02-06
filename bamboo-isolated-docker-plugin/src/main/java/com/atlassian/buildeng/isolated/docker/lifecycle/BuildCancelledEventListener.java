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

import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.event.BuildCanceledEvent;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.events.AgentOfflineEvent;
import com.atlassian.buildeng.isolated.docker.AgentQueries;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.event.api.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * when build is cancelled on docker agents we want to remove them.
 * @author mkleint
 */
public class BuildCancelledEventListener {
    private final Logger LOG = LoggerFactory.getLogger(BuildCancelledEventListener.class);
    private final AgentRemovals agentRemovals;
    private final AgentManager agentManager;

    public BuildCancelledEventListener(AgentRemovals agentRemovals, AgentManager agentManager) {
        this.agentRemovals = agentRemovals;
        this.agentManager = agentManager;
    }
    
    @EventListener
    public void onCancelledBuild(BuildCanceledEvent event) {
        Long agentId = event.getAgentId();
        if (agentId != null) {
            BuildAgent agent = agentManager.getAgent(agentId);
            if (AgentQueries.isDockerAgent(agent)) {
                LOG.info("Stopping docker agent for cancelled build {} {}:{}", event.getBuildResultKey(), agent.getName(), agentId);
                agentRemovals.stopAgentRemotely(agent);
            }
        }
    }
    
    @EventListener
    public void onOfflineAgent(AgentOfflineEvent event) {
        //only remove enabled ones, to cater for the capability-requirement mismatch ones
        // that we want to keep. sort of ugly sorting criteria but there are little ways of
        //adding custom data to agents at runtime.
        if (AgentQueries.isEnabledDockerAgent(event.getBuildAgent())) {
            agentRemovals.removeAgent(event.getBuildAgent());
        }
    }
}
