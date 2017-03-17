/*
 * Copyright 2017 Atlassian Pty Ltd.
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

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.buildqueue.PipelineDefinition;
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableBuildable;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityRequirementsMatcher;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.fugue.Iterables;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 *
 * @author mkleint
 */
public class UnmetRequirements {

    private final BuildQueueManager buildQueueManager;
    private final CachedPlanManager cachedPlanManager;
    private final CapabilityRequirementsMatcher capabilityRequirementsMatcher;
    private final AgentManager agentManager;

    public UnmetRequirements(BuildQueueManager buildQueueManager, CachedPlanManager cachedPlanManager, CapabilityRequirementsMatcher capabilityRequirementsMatcher, AgentManager agentManager) {
        this.buildQueueManager = buildQueueManager;
        this.cachedPlanManager = cachedPlanManager;
        this.capabilityRequirementsMatcher = capabilityRequirementsMatcher;
        this.agentManager = agentManager;
    }

    /**
     * check if requirements match the capabilities of the agent. it will disable the agent + remove job from queue
     * when so.
     * @param pipelineDefinition
     * @return true when agent can be removed next to stopped.
     */
    public boolean markAndStopTheBuild(RemoteAgentDefinition pipelineDefinition) {
        final CapabilitySet capabilitySet = pipelineDefinition.getCapabilitySet();
        if (capabilitySet == null) {
            return true;
        }
        Capability resultCap = capabilitySet.getCapability(Constants.CAPABILITY_RESULT);
        if (resultCap != null) {
            String resultKey = resultCap.getValue();
            final PlanResultKey key = PlanKeys.getPlanResultKey(resultKey);
            QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, (BuildQueueManager.QueueItemView<CommonContext> input) -> input);
            Optional<BuildQueueManager.QueueItemView<CommonContext>> found = StreamSupport.stream(queue.getQueueView(Iterables.emptyIterable()).spliterator(), false)
                    .filter((BuildQueueManager.QueueItemView<CommonContext> t) -> key.equals(t.getQueuedResultKey().getResultKey()))
                    .findFirst();
            if (found.isPresent() && found.get().getView() instanceof BuildContext) {
                CurrentResult current = found.get().getView().getCurrentResult();
                final ImmutableBuildable build = cachedPlanManager.getPlanByKey(key.getPlanKey(), ImmutableBuildable.class);
                if (build != null) {
                    //only builds
                    RequirementSet req = build.getEffectiveRequirementSet();
                    if (!capabilityRequirementsMatcher.matches(capabilitySet, req)) {
                        current.getCustomBuildData().put(Constants.RESULT_ERROR, "Capabilities of agent don't match requirements. Check the <a href=\"/admin/agent/viewAgent.action?agentId=" + pipelineDefinition.getId() + "\">agent's capabilities.</a>");
                        current.getCustomBuildData().put(Constants.RESULT_AGENT_KILLED_ITSELF, "false");
                        current.setLifeCycleState(LifeCycleState.NOT_BUILT);
                        PipelineDefinition pd = pipelineDefinition;
                        pd.setEnabled(false);
                        agentManager.savePipeline(pd);
                        buildQueueManager.removeBuildFromQueue(found.get().getView().getResultKey());
//                        errorUpdateHandler.recordError(t.getView().getEntityKey(), "Build was not queued due to error:" + error);
//                        eventPublisher.publish(new DockerAgentRemoteFailEvent(error, t.getView().getEntityKey()));
                        return false;
                    }
                }
            }
            if (found.isPresent() && found.get().getView() instanceof DeploymentContext) {
                //TODO no idea how to deal with requirements matching in deployments.
            }
        }
        return true;
    }
}
