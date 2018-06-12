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
import com.atlassian.bamboo.buildqueue.manager.AgentAssignmentMap;
import com.atlassian.bamboo.buildqueue.manager.AgentAssignmentService;
import com.atlassian.bamboo.buildqueue.manager.AgentAssignmentServiceHelper;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableBuildable;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
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
import com.atlassian.buildeng.isolated.docker.events.DockerAgentDedicatedJobEvent;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentNonMatchedRequirementEvent;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.event.api.EventPublisher;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UnmetRequirements {

    private final BuildQueueManager buildQueueManager;
    private final CachedPlanManager cachedPlanManager;
    private final CapabilityRequirementsMatcher capabilityRequirementsMatcher;
    private final AgentManager agentManager;
    private final AgentRemovals agentRemovals;
    private final ErrorUpdateHandler errorUpdateHandler;
    private final EventPublisher eventPublisher;
    private final AgentAssignmentService agentAssignmentService;

    public UnmetRequirements(BuildQueueManager buildQueueManager, CachedPlanManager cachedPlanManager, 
                            AgentManager agentManager, AgentRemovals agentRemovals,
                            AgentAssignmentService agentAssignmentService,
                            ErrorUpdateHandler errorUpdateHandler, EventPublisher eventPublisher) {
        this.buildQueueManager = buildQueueManager;
        this.cachedPlanManager = cachedPlanManager;
        this.capabilityRequirementsMatcher = new CapabilityRequirementsMatcherImpl();
        this.agentManager = agentManager;
        this.agentRemovals = agentRemovals;
        this.agentAssignmentService = agentAssignmentService;
        this.errorUpdateHandler = errorUpdateHandler;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * check if requirements match the capabilities of the agent. 
     * it will disable the agent+ stop the agent + remove job from queue when so.
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
            Optional<CommonContext> found = DockerAgentBuildQueue.currentlyQueued(buildQueueManager)
                    .filter((CommonContext t) -> key.equals(t.getResultKey()))
                    .findFirst();
            if (found.isPresent() && found.get() instanceof BuildContext) {
                CurrentResult current = found.get().getCurrentResult();
                final ImmutableBuildable build = cachedPlanManager.getPlanByKey(key.getPlanKey(), 
                        ImmutableBuildable.class);
                if (build != null) {
                    //only builds
                    RequirementSet req = build.getEffectiveRequirementSet();
                    if (!capabilityRequirementsMatcher.matches(capabilitySet, req)) {
                        List<String> missingReqKeys = findMissingRequirements(capabilitySet, req);
                        current.getCustomBuildData().put(Constants.RESULT_ERROR,
                                "Capabilities of <a href=\"/admin/agent/viewAgent.action?agentId="
                                        + pipelineDefinition.getId()
                                        + "\">agent</a> don't match requirements.</br>Capabilities of agent:</br>"
                                        + capabilitySet.getCapabilities().stream().map(
                                                (Capability c) -> c.getKey() + "=" + c.getValueWithDefault()
                                ).collect(Collectors.joining("<br>")));
                        current.getCustomBuildData().put(Constants.RESULT_AGENT_KILLED_ITSELF, "false");
                        current.setLifeCycleState(LifeCycleState.NOT_BUILT);
                        pipelineDefinition.setEnabled(false);
                        agentManager.savePipeline(pipelineDefinition);
                        buildQueueManager.removeBuildFromQueue(found.get().getResultKey());
                        // stop agent but do not remove it yet. Wait until ReaperJob comes around and kills it after 40
                        // minutes to allow time for inspection by the user.
                        agentRemovals.stopAgentRemotely(pipelineDefinition.getId());
                        errorUpdateHandler.recordError(found.get().getEntityKey(),
                                "Capabilities of PBC agent don't match job " + key.getPlanKey()
                                        + " requirements (" + found.get().getResultKey() 
                                        + "). Affected requirements:" + missingReqKeys);
                        eventPublisher.publish(new DockerAgentNonMatchedRequirementEvent(found.get().getEntityKey(),
                                missingReqKeys));
                        return true;
                    }
                    AgentAssignmentMap assignments = agentAssignmentService.getAgentAssignments();
                    //because build.getMaster() is null
                    PlanKey planKey = PlanKeys.getChainKeyIfJobKey(key.getPlanKey());
                    ImmutablePlan plan = cachedPlanManager.getPlanByKey(planKey);
                    Set<AgentAssignmentService.AgentAssignmentExecutor> dedicatedAgents = assignments.forExecutables(
                            Iterables.concat(
                                AgentAssignmentServiceHelper.asExecutables(build),
                                AgentAssignmentServiceHelper.asExecutables(plan),
                                AgentAssignmentServiceHelper.projectToExecutables(plan.getProject())));
                    if (!dedicatedAgents.isEmpty()) {
                        current.getCustomBuildData().put(Constants.RESULT_ERROR,
                                "Please <a href=\"https://confluence.atlassian.com/display/BAMBOO/Dedicating+an+agent\">undedictate</a> this job or it's plan from building on specific remote agent or elastic image. It cannot run on per-build container agents.");
                        current.getCustomBuildData().put(Constants.RESULT_AGENT_KILLED_ITSELF, "false");
                        current.setLifeCycleState(LifeCycleState.NOT_BUILT);
                        buildQueueManager.removeBuildFromQueue(found.get().getResultKey());
                        agentRemovals.stopAgentRemotely(pipelineDefinition.getId());
                        agentRemovals.removeAgent(pipelineDefinition.getId());
                        errorUpdateHandler.recordError(found.get().getEntityKey(),
                                "Please undedictate job " + key.getPlanKey() + " or it's plan from building on specific remote agent or elastic image. It cannot run on per-build container agents.");
                        eventPublisher.publish(new DockerAgentDedicatedJobEvent(found.get().getEntityKey()));
                        return true;
                    }
                }
            }
            if (found.isPresent() && found.get() instanceof DeploymentContext) {
                //TODO no idea how to deal with requirements matching in deployments.
                //agentAssignmentService.isCapabilitiesMatch() could be the way.
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
