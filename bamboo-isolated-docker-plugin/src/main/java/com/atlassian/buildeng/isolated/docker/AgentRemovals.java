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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.messages.StopAgentNicelyMessage;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BambooComponent
public class AgentRemovals {
    private static final Logger logger = LoggerFactory.getLogger(AgentRemovals.class);

    private final AgentManager agentManager;
    private final AgentCommandSender agentCommandSender;

    @Inject
    public AgentRemovals(AgentManager agentManager, AgentCommandSender agentCommandSender) {
        this.agentManager = agentManager;
        this.agentCommandSender = agentCommandSender;
    }

    public void stopAgentRemotely(BuildAgent buildAgent) {
        // Stop the agent
        Long agentId = buildAgent.getId();
        String agentName = buildAgent.getName();
        buildAgent.setRequestedToBeStopped(true); // Set status correctly
        agentCommandSender.send(new StopAgentNicelyMessage(), agentId);
        logger.debug("Sent remote message to stop PBC agent {} (id: {})", agentName, agentId);
    }

    public void stopAgentRemotely(long agentId) {
        BuildAgent ba = agentManager.getAgent(agentId);
        if (ba != null) {
            stopAgentRemotely(ba);
        }
    }

    public void removeAgent(BuildAgent agent) {
        if (agent != null) {
            //we intentionally call the Long paramed method here.
            removeAgent(agent.getId());
        }
    }

    //synchronized because we have different things that could step on each other's toes
    public synchronized void removeAgent(long agentId) {
        BuildAgent ba = agentManager.getAgent(agentId); //double check the agent still exists.
        if (ba != null) {
            String agentName = ba.getName();
            try {
                agentManager.removeAgent(agentId);        // Remove agent from the UI/server side
                logger.debug("Successfully removed agent {} (id: {})", agentName, agentId);
            } catch (TimeoutException e) {
                logger.error(String.format("timeout on removing agent %s (id: %s)", agentName, agentId), e);
            }
        }
    }


}
