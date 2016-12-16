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
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.deployment.RequirementTaskConfigurator;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;
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
            File metadata = new File(Constants.METADATA_FILE_PATH);
            if (metadata.isFile()) {
                try (FileReader r = new FileReader(metadata.getPath())) {
                    JsonElement topLevel = new Gson().fromJson(r, JsonElement.class);
                    if (topLevel != null && topLevel.isJsonArray()) {
                        topLevel.getAsJsonArray().forEach(jsonElement -> {
                            JsonObject curr = jsonElement.getAsJsonObject();
                            JsonElement nameObj = curr.get("name");
                            if (nameObj != null && !nameObj.getAsString().equals(Constants.METADATA_CONTAINER_NAME) && !nameObj.getAsString().equals(Constants.AMAZON_MAGIC_VOLUME_NAME)) {
                                String hash = curr.get("hash").getAsString();
                                String tag  = curr.get("tag").getAsString();
                                buildLogger.addBuildLogEntry(String.format("Docker image '%s' had hash: %s", tag, hash ));
                            }
                        });
                    }
                } catch (JsonSyntaxException ex) {
                    buildLogger.addBuildLogEntry("Metadata found not proper json");
                }
            } else {
                buildLogger.addBuildLogEntry("No metadata found");
            }
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

    public static Collection<Pair<String, String>> getImageSizes() {
        return Lists.newArrayList(
                //this is stupid ordering but we want to keep regular as default for new
                //config. but somehow unlike with tasks there's no way to get the defaults propagated into UI.
                Pair.make(Configuration.ContainerSize.REGULAR.name(), "Regular (~8G memory)"),
                Pair.make(Configuration.ContainerSize.LARGE.name(), "Large (~12G memory)"),
                Pair.make(Configuration.ContainerSize.SMALL.name(), "Small (~4G memory)"));
    }

}
