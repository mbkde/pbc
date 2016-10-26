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

package com.atlassian.buildeng.isolated.docker.lifecycle;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomPreBuildAction;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BaseConfigurablePlugin;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementSet;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.deployment.RequirementTaskConfigurator;
import com.google.common.collect.Lists;
import java.util.Collection;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CustomPreBuildActionImpl extends BaseConfigurablePlugin implements CustomPreBuildAction {

    private final Logger LOG = LoggerFactory.getLogger(CustomPreBuildActionImpl.class);
    private BuildContext buildContext;
    private BuildLoggerManager buildLoggerManager;

    public CustomPreBuildActionImpl() {
    }

    public BuildLoggerManager getBuildLoggerManager() {
        return buildLoggerManager;
    }

    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager) {
        this.buildLoggerManager = buildLoggerManager;
    }

    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() throws Exception {
        Configuration config = AccessConfiguration.forContext(buildContext);
        if (config.isEnabled()) {
            BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());
            buildLogger.addBuildLogEntry("Docker image " + config.getDockerImage() + " used to build this job");
        }
        return buildContext;
    }

    @Override
    public void customizeBuildRequirements(@NotNull PlanKey planKey, @NotNull BuildConfiguration buildConfiguration, @NotNull RequirementSet requirementSet) {
        removeBuildRequirements(planKey, buildConfiguration, requirementSet);
        Configuration config = AccessConfiguration.forBuildConfiguration(buildConfiguration);
        if (config.isEnabled()) {
            requirementSet.addRequirement(new RequirementImpl(Constants.CAPABILITY, false, config.getDockerImage(), true));
        }
    }

    @NotNull
    @Override
    public ErrorCollection validate(@NotNull BuildConfiguration bc) {
        String v = bc.getString(Configuration.DOCKER_EXTRA_CONTAINERS);
        SimpleErrorCollection errs = new SimpleErrorCollection();
        RequirementTaskConfigurator.validateExtraContainers(v, errs);
        Configuration config = AccessConfiguration.forBuildConfiguration(bc);
        if (config.isEnabled() && StringUtils.isBlank(config.getDockerImage())) {
            errs.addError(Configuration.DOCKER_IMAGE, "Docker image cannot be blank.");
        }
        if (errs.hasAnyErrors()) {
            return errs;
        }
        //TODO more checks on format.
        return super.validate(bc);
    }


    @Override
    public void removeBuildRequirements(@NotNull PlanKey planKey, @NotNull BuildConfiguration buildConfiguration, @NotNull RequirementSet requirementSet) {
        requirementSet.removeRequirements((Requirement input) -> input.getKey().equals(Constants.CAPABILITY));
    }

    @Override
    protected void populateContextForEdit(@NotNull Map<String, Object> context, @NotNull BuildConfiguration buildConfiguration, Plan plan) {
        super.populateContextForEdit(context, buildConfiguration, plan);
        Configuration config = AccessConfiguration.forBuildConfiguration(buildConfiguration);
        config.copyTo(context);
        context.put("imageSizes", getImageSizes());
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration) {
        super.addDefaultValues(buildConfiguration);
        buildConfiguration.addProperty(Configuration.ENABLED_FOR_JOB, false);
        buildConfiguration.addProperty(Configuration.DOCKER_IMAGE, "docker:xxx");
    }

    public static Collection<Pair<String, String>> getImageSizes() {
        return Lists.newArrayList(Pair.make(Configuration.ContainerSize.REGULAR.name(), "Regular (~8G memory)"),
                Pair.make(Configuration.ContainerSize.SMALL.name(), "Small (~4G memory)"));
    }

}
