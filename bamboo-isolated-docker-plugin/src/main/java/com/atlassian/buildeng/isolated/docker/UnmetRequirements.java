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
import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableBuildable;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityRequirementsMatcher;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityRequirementsMatcherImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentNonMatchedRequirementEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private final AgentRemovals agentRemovals;
    private final ErrorUpdateHandler errorUpdateHandler;
    private final EventPublisher eventPublisher;

    public UnmetRequirements(BuildQueueManager buildQueueManager, CachedPlanManager cachedPlanManager, AgentManager agentManager, AgentRemovals agentRemovals, 
                            ErrorUpdateHandler errorUpdateHandler, EventPublisher eventPublisher) {
        this.buildQueueManager = buildQueueManager;
        this.cachedPlanManager = cachedPlanManager;
        this.capabilityRequirementsMatcher = new CapabilityRequirementsMatcherImpl();
        this.agentManager = agentManager;
        this.agentRemovals = agentRemovals;
        this.errorUpdateHandler = errorUpdateHandler;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * check if requirements match the capabilities of the agent. it will disable the agent+ stop the agent + remove job from queue
     * when so.
     * @param pipelineDefinition
     * @return true when unmatched requirements were detected;
     */
    public boolean markAndStopTheBuild(RemoteAgentDefinition pipelineDefinition) {
        final CapabilitySet capabilitySet = pipelineDefinition.getCapabilitySet();
        if (capabilitySet == null) {
            return false;
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
                        List<String> missingReqKeys = findMissingRequirements(capabilitySet, req);
                        current.getCustomBuildData().put(Constants.RESULT_ERROR, "Capabilities of agent don't match requirements. Check the <a href=\"/admin/agent/viewAgent.action?agentId=" + pipelineDefinition.getId() + "\">agent's capabilities.</a></br>Affected requirements:" + missingReqKeys);
                        current.getCustomBuildData().put(Constants.RESULT_AGENT_KILLED_ITSELF, "false");
                        current.setLifeCycleState(LifeCycleState.NOT_BUILT);
                        pipelineDefinition.setEnabled(false);
                        agentManager.savePipeline(pipelineDefinition);
                        buildQueueManager.removeBuildFromQueue(found.get().getView().getResultKey());
                        agentRemovals.stopAgentRemotely(pipelineDefinition.getId());
                        errorUpdateHandler.recordError(found.get().getView().getEntityKey(), "Capabilities of PBC agent don't match job's requirements (" + found.get().getView().getResultKey() + "). Affected requirements:" + missingReqKeys);
                        eventPublisher.publish(new DockerAgentNonMatchedRequirementEvent(found.get().getView().getEntityKey(), missingReqKeys));
                        return true;
                    }
                }
            }
            if (found.isPresent() && found.get().getView() instanceof DeploymentContext) {
                //TODO no idea how to deal with requirements matching in deployments.
            }
        }
        return false;
    }

    private List<String> findMissingRequirements(CapabilitySet capabilitySet, RequirementSet req) {
        return req.getRequirements().stream()
                .filter((Requirement t) -> !capabilityRequirementsMatcher.matches(capabilitySet, t))
                .map((Requirement t) -> t.getKey())
                .collect(Collectors.toList());
    }
}