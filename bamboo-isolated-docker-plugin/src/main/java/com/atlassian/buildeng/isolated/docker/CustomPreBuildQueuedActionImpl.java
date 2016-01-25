/*
 * Copyright 2015 Atlassian.
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

import com.atlassian.bamboo.buildqueue.manager.CustomPreBuildQueuedAction;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BaseConfigurablePlugin;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPreBuildQueuedActionImpl extends BaseConfigurablePlugin implements CustomPreBuildQueuedAction {

    private final IsolatedAgentService isolatedAgentService;
    private BuildContext buildContext;
    private final Logger LOG = LoggerFactory.getLogger(CustomPreBuildQueuedActionImpl.class);


    public CustomPreBuildQueuedActionImpl(IsolatedAgentService isolatedAgentService, TemplateRenderer renderer) {
        this.isolatedAgentService = isolatedAgentService;
        setTemplateRenderer(renderer);
    }


    @Override
    public void init(BuildContext bc) {
        this.buildContext = bc;
    }

    @Override
    public BuildContext call() throws InterruptedException, Exception {
        Configuration config = Configuration.forBuildContext(buildContext);
        if (config.isEnabled()) {
            buildContext.getBuildResult().getCustomBuildData().put(Constants.RESULT_TIME_QUEUED, "" + System.currentTimeMillis());
            isolatedAgentService.startInstance(new IsolatedDockerAgentRequest(config.getDockerImage()));
        }
        return buildContext;
    }

    @Override
    public void customizeBuildRequirements(PlanKey planKey, BuildConfiguration buildConfiguration, RequirementSet requirementSet) {
        removeBuildRequirements(planKey, buildConfiguration, requirementSet);
        Configuration config = Configuration.forBuildConfiguration(buildConfiguration);
        if (config.isEnabled()) {
            requirementSet.addRequirement(new RequirementImpl(Constants.CAPABILITY, false, config.getDockerImage(), true));
        }
    }

    @Override
    public ErrorCollection validate(BuildConfiguration bc) {
        Configuration config = Configuration.forBuildConfiguration(bc);
        if (config.isEnabled() && StringUtils.isBlank(config.getDockerImage())) {
            return new SimpleErrorCollection("Docker image cannot be blank.");
        }
        //TODO more checks on format.
        return super.validate(bc);
    }



    @Override
    public void removeBuildRequirements(PlanKey planKey, BuildConfiguration buildConfiguration, RequirementSet requirementSet) {
        requirementSet.removeRequirements((Requirement input) -> input.getKey().equals(Constants.CAPABILITY));
    }

    @Override
    protected void populateContextForEdit(Map<String, Object> context, BuildConfiguration buildConfiguration, Plan plan) {
        super.populateContextForEdit(context, buildConfiguration, plan);
        Configuration config = Configuration.forBuildConfiguration(buildConfiguration);
        context.put(Constants.ENABLED_FOR_JOB, config.isEnabled());
        context.put(Constants.DOCKER_IMAGE, config.getDockerImage());
    }

    @Override
    public void addDefaultValues(@NotNull final BuildConfiguration buildConfiguration)
    {
        super.addDefaultValues(buildConfiguration);
        buildConfiguration.addProperty(Constants.ENABLED_FOR_JOB, false);
        buildConfiguration.addProperty(Constants.DOCKER_IMAGE, "docker:xxx");
    }

}
