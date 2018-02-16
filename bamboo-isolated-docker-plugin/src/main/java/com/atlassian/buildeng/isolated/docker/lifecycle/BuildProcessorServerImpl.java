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

import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.CustomBuildProcessorServer;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BaseConfigurablePlugin;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.CurrentlyBuilding;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.deployment.RequirementTaskConfigurator;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
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
public class BuildProcessorServerImpl extends BaseConfigurablePlugin implements CustomBuildProcessorServer {
    private static final Logger LOG = LoggerFactory.getLogger(BuildProcessorServerImpl.class);
    static final String CAPABILITY = Capability.SYSTEM_PREFIX + ".isolated.docker";

    private BuildContext buildContext;
    private AgentRemovals agentRemovals;
    private BuildExecutionManager buildExecutionManager;

    //setters here for components, otherwise the parent fields don't get injected.
    public BuildProcessorServerImpl() {
    }

    public AgentRemovals getAgentRemovals() {
        return agentRemovals;
    }

    public void setAgentRemovals(AgentRemovals agentRemovals) {
        this.agentRemovals = agentRemovals;
    }

    public BuildExecutionManager getBuildExecutionManager() {
        return buildExecutionManager;
    }

    public void setBuildExecutionManager(BuildExecutionManager buildExecutionManager) {
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
        // and our StopDockerAgentBuildProcessor is not executed. absence of the marker property 
        // tells us that we didn't run on agent
        if (conf.isEnabled() &&  null == buildResult.getCustomBuildData().get(Constants.RESULT_AGENT_KILLED_ITSELF)) {
            CurrentlyBuilding building = buildExecutionManager.getCurrentlyBuildingByBuildResult(buildContext);
            Long agentId = null;
            if (building != null) {
                agentId = building.getBuildAgentId();
            }
            if (building != null && agentId != null) {
                agentRemovals.stopAgentRemotely(agentId);
                agentRemovals.removeAgent(agentId);
                LOG.info("Build result {} not shutting down normally, killing agent {} explicitly.", 
                        buildContext.getBuildResultKey(), agentId);
            } else {
                LOG.warn("Agent for {} not found. Cannot stop the agent.", buildContext.getBuildResultKey());
            }
            
        }
        return buildContext;
    }
    
    // TODO eventually remove CAPABILITY once we are sure noone is using it anymore.
    @Override
    public void customizeBuildRequirements(@NotNull PlanKey planKey, @NotNull BuildConfiguration buildConfiguration, 
            @NotNull RequirementSet requirementSet) {
        removeBuildRequirements(planKey, buildConfiguration, requirementSet);
        Configuration config = AccessConfiguration.forBuildConfiguration(buildConfiguration);
        if (config.isEnabled()) {
            requirementSet.addRequirement(new RequirementImpl(Constants.CAPABILITY_RESULT, true, ".*", true));
        }
    }

    // TODO eventually remove CAPABILITY once we are sure noone is using the it anymore.
    @Override
    public void removeBuildRequirements(@NotNull PlanKey planKey, @NotNull BuildConfiguration buildConfiguration, 
            @NotNull RequirementSet requirementSet) {
        requirementSet.removeRequirements((Requirement input) -> 
                input.getKey().equals(CAPABILITY) || input.getKey().equals(Constants.CAPABILITY_RESULT));
    }

    @NotNull
    @Override
    public ErrorCollection validate(@NotNull BuildConfiguration bc) {
        String v = bc.getString(Configuration.DOCKER_EXTRA_CONTAINERS);
        SimpleErrorCollection errs = new SimpleErrorCollection();
        RequirementTaskConfigurator.validateExtraContainers(v, errs);
        Configuration config = AccessConfiguration.forBuildConfiguration(bc);
        if (config.isEnabled()) {
            if (StringUtils.isBlank(config.getDockerImage())) {
                errs.addError(Configuration.DOCKER_IMAGE, "Docker image cannot be blank.");
            } else if (!config.getDockerImage().trim().equals(config.getDockerImage())) {
                errs.addError(Configuration.DOCKER_IMAGE, "Docker image cannot contain whitespace.");
            }
        }
        if (errs.hasAnyErrors()) {
            return errs;
        }
        //TODO more checks on format.
        return super.validate(bc);
    }


    @Override
    protected void populateContextForEdit(@NotNull Map<String, Object> context, 
            @NotNull BuildConfiguration buildConfiguration, Plan plan) {
        super.populateContextForEdit(context, buildConfiguration, plan);
        Configuration config = AccessConfiguration.forBuildConfiguration(buildConfiguration);
        config.copyTo(context);
        context.put("imageSizes", getImageSizes());
    }

    @NotNull
    public static Collection<Pair<String, String>> getImageSizes() {
        return Arrays.asList(
                //this is stupid ordering but we want to keep regular as default for new
                //config. but somehow unlike with tasks there's no way to get the defaults propagated into UI.
                Pair.make(Configuration.ContainerSize.REGULAR.name(), "Regular (~8G memory, 2 vCPU)"),
                Pair.make(Configuration.ContainerSize.SMALL.name(), "Small (~4G memory, 1 vCPU)"),
                Pair.make(Configuration.ContainerSize.LARGE.name(), "Large (~12G memory, 3 vCPU)"),
                Pair.make(Configuration.ContainerSize.XLARGE.name(), "Extra Large (~16G memory, 4 vCPU)"));
    }
    
}
