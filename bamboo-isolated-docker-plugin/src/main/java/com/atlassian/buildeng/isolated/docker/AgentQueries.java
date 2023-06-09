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

import com.atlassian.bamboo.ResultKey;
import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.EphemeralAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

public class AgentQueries {

    public static boolean isDockerAgentForResult(BuildAgent agent, @Nonnull ResultKey key) {
        String cap = getDockerResultCapability(agent);
        String ephemeralKey = getResultKeyForEphemeralAgent(agent);
        return (cap != null && key.getKey().equals(cap))
                || (ephemeralKey != null && key.getKey().equals(ephemeralKey));
    }

    public static boolean isDockerAgent(BuildAgent agent) {
        return getDockerResultCapability(agent) != null || getDockerEphemeralAgent(agent) != null;
    }

    public static boolean isEnabledDockerAgent(BuildAgent agent) {
        if (agent == null || !agent.isEnabled()) {
            return false;
        }
        return isDockerAgent(agent);
    }

    private static String getDockerResultCapability(BuildAgent agent) {
        if (agent == null) {
            return null;
        }
        AtomicReference<String> ref = new AtomicReference<>();
        agent.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {}

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {}

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                getCapabilityValue(pipelineDefinition, Constants.CAPABILITY_RESULT)
                        .ifPresent(ref::set);
            }

            @Override
            // Don't count ephemeral agents as "docker agents" for the purposes of ReaperJob queries
            public void visitEphemeral(EphemeralAgentDefinition pipelineDefinition) {}
        });
        return ref.get();
    }

    private static String getDockerEphemeralAgent(BuildAgent agent) {
        if (agent == null) {
            return null;
        }
        AtomicReference<String> ref = new AtomicReference<>();
        agent.getDefinition().accept(new PipelineDefinitionVisitor() {
            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {}

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {}

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                // So called ephemeral agents in PBC are still considered "Remote agents" in Bamboo.
                getCapabilityValue(pipelineDefinition, Constants.EPHEMERAL_CAPABILITY_RESULT)
                        .ifPresent(ref::set);
            }

            @Override
            public void visitEphemeral(EphemeralAgentDefinition pipelineDefinition) {}
        });
        return ref.get();
    }

    private static String getResultKeyForEphemeralAgent(BuildAgent agent) {
        if (agent == null) {
            return null;
        }
        AtomicReference<String> ref = new AtomicReference<>();
        agent.getDefinition().accept(new PipelineDefinitionVisitor() {

            @Override
            public void visitElastic(ElasticAgentDefinition pipelineDefinition) {}

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {}

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                // So called ephemeral agents in PBC are still considered "Remote agents" in Bamboo.
                Optional.ofNullable(pipelineDefinition.getEphemeralAgentDedication())
                        .map(ResultKey::getKey)
                        .ifPresent(ref::set);
            }

            @Override
            public void visitEphemeral(EphemeralAgentDefinition pipelineDefinition) {}
        });
        return ref.get();
    }

    @VisibleForTesting
    public static Optional<String> getCapabilityValue(RemoteAgentDefinition pipelineDefinition, String capabilityKey) {
        return Optional.ofNullable(pipelineDefinition.getCapabilitySet())
                .map(set -> set.getCapability(capabilityKey))
                .map(Capability::getValue);
    }
}
