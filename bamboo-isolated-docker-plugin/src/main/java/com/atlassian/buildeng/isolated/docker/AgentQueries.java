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

import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

public class AgentQueries {

    public static boolean isDockerAgentForResult(BuildAgent t, @Nonnull PlanResultKey key) {
        String cap = getDockerResultCapability(t);
        return cap != null && key.equals(PlanKeys.getPlanResultKey(cap));
    }

    public static boolean isDockerAgent(BuildAgent agent) {
        return getDockerResultCapability(agent) != null;
    }

    public static boolean isEnabledDockerAgent(BuildAgent agent) {
        if (agent == null || !agent.isEnabled()) {
            return false;
        }
        return isDockerAgent(agent);
    }

    public static String getDockerResultCapability(BuildAgent agent) {
        if (agent == null) {
            return null;
        }
        AtomicReference<String> ref = new AtomicReference<>();
        agent.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                if (pipelineDefinition.getCapabilitySet() != null) {
                    CapabilitySet capabilitySet = pipelineDefinition.getCapabilitySet();
                    if (capabilitySet != null) {
                        Capability cap = capabilitySet.getCapability(Constants.CAPABILITY_RESULT);
                        if (cap != null) {
                            ref.set(cap.getValue());
                        }
                    }
                }
            }
        });
        return ref.get();
    }

}
