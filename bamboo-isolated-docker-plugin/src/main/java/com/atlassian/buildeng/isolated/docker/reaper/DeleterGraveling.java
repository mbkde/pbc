package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.buildqueue.ElasticAgentDefinition;
import com.atlassian.bamboo.buildqueue.LocalAgentDefinition;
import com.atlassian.bamboo.buildqueue.PipelineDefinitionVisitor;
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
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgent.BuildAgentVisitor;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityRequirementsMatcher;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityRequirementsMatcherImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.v2.build.agent.messages.StopAgentNicelyMessage;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.fugue.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class DeleterGraveling implements BuildAgentVisitor {
    private final AgentCommandSender agentCommandSender;
    private final AgentManager agentManager;
    private final BuildQueueManager buildQueueManager;
    private final CachedPlanManager cachedPlanManager;
    private final CapabilityRequirementsMatcher capabilityRequirementsMatcher;

    private final static Logger LOG = LoggerFactory.getLogger(DeleterGraveling.class);

    public DeleterGraveling(AgentCommandSender agentCommandSender, AgentManager agentManager, 
            BuildQueueManager buildQueueManager, CachedPlanManager cachedPlanManager) {
        this.agentCommandSender = agentCommandSender;
        this.agentManager = agentManager;
        this.buildQueueManager = buildQueueManager;
        this.cachedPlanManager = cachedPlanManager;
        this.capabilityRequirementsMatcher = new CapabilityRequirementsMatcherImpl();
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
                        buildAgent.getType(), buildAgent.getAgentStatus().isIdle(),
                        buildAgent.getName());
            }

            @Override
            public void visitLocal(LocalAgentDefinition pipelineDefinition) {
            }

            @Override
            public void visitRemote(RemoteAgentDefinition pipelineDefinition) {
                boolean remove = markAndStopTheBuild(buildAgent, pipelineDefinition);
                stopAgentRemotely(buildAgent, remove, agentManager, agentCommandSender);
            }

        });
    }

    private static void stopAgentRemotely(BuildAgent buildAgent, boolean remove, AgentManager agentManager, AgentCommandSender agentCommandSender) {
        // Stop the agent
        Long agentId = buildAgent.getId();
        String agentName = buildAgent.getName();
        try {
            buildAgent.setRequestedToBeStopped(true); // Set status correctly
            agentCommandSender.send(new StopAgentNicelyMessage(), agentId);
            if (remove) {
                agentManager.removeAgent(agentId);        // Remove agent from the UI/server side
            }
            LOG.info("Successfully reaped agent {} (id: {})", agentName, agentId);
        } catch (TimeoutException e) {
            LOG.error(String.format("timeout on removing agent %s (id: %s)", agentName, agentId), e);
        }
    }
    
    public static void stopAgentRemotely(BuildAgent buildAgent, AgentManager agentManager, AgentCommandSender agentCommandSender) {
        stopAgentRemotely(buildAgent, true, agentManager, agentCommandSender);
    }
    
    private boolean markAndStopTheBuild(BuildAgent buildAgent, RemoteAgentDefinition pipelineDefinition) {
        final CapabilitySet capabilitySet = pipelineDefinition.getCapabilitySet();
        if (capabilitySet == null) {
            return true;
        }
        Capability resultCap = capabilitySet.getCapability(Constants.CAPABILITY_RESULT);
        if (resultCap != null) {
            String resultKey = resultCap.getValue();
            final PlanResultKey key = PlanKeys.getPlanResultKey(resultKey);
            QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager, (BuildQueueManager.QueueItemView<CommonContext> input) -> input);
            final CommonContext[] found = new CommonContext[1];
            queue.getQueueView(Iterables.emptyIterable()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
                if (key.equals(t.getQueuedResultKey().getResultKey())) {
                    found[0] = t.getView();
                }
            });
            if (found[0] != null && found[0] instanceof BuildContext) {
                CurrentResult current = found[0].getCurrentResult();
                final ImmutableBuildable build = cachedPlanManager.getPlanByKey(key.getPlanKey(), ImmutableBuildable.class);
                if (build != null) {
                    //only builds
                    RequirementSet req = build.getEffectiveRequirementSet();
                    if (!capabilityRequirementsMatcher.matches(capabilitySet, req)) {
                        current.getCustomBuildData().put(Constants.RESULT_ERROR, "Capabilities of agent don't match requirements. Check the <a href=\"/admin/agent/viewAgent.action?agentId=" +  buildAgent.getId() + "\">agent's capabilities.</a>");
                        current.getCustomBuildData().put(Constants.RESULT_AGENT_KILLED_ITSELF, "false");
                        current.setLifeCycleState(LifeCycleState.NOT_BUILT);
                        buildQueueManager.removeBuildFromQueue(found[0].getResultKey());
//                        errorUpdateHandler.recordError(t.getView().getEntityKey(), "Build was not queued due to error:" + error);
//                        eventPublisher.publish(new DockerAgentRemoteFailEvent(error, t.getView().getEntityKey()));
                        return false;
                    }
                }
            }
            if (found[0] != null && found[0] instanceof DeploymentContext) {
                //TODO no idea how to deal with requirements matching in depployments.
            }
        }
        return true;
    }
}
