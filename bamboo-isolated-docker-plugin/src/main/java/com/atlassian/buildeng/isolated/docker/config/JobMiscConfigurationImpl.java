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
package com.atlassian.buildeng.isolated.docker.config;

import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanType;
import com.atlassian.bamboo.v2.build.BaseConfigurablePlugin;
import com.atlassian.bamboo.v2.build.configuration.MiscellaneousBuildConfigurationPlugin;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.isolated.docker.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JobMiscConfigurationImpl extends BaseConfigurablePlugin implements MiscellaneousBuildConfigurationPlugin
{
    @Override
    public boolean isApplicableTo(Plan plan)
    {
        Class jobClass = PlanType.JOB.getImmutableClassType();
        return jobClass.isInstance(plan);
    }

    @Override
    protected void populateContextForEdit(@NotNull final Map<String, Object> context, 
            @NotNull final BuildConfiguration buildConfiguration,
            @Nullable final Plan plan)
    {
        super.populateContextForEdit(context, buildConfiguration, plan);

        context.put(Constants.ENABLED_FOR_JOB, buildConfiguration.getBoolean(Constants.ENABLED_FOR_JOB));
    }


    @Override
    public void addDefaultValues(@NotNull final BuildConfiguration buildConfiguration)
    {
        super.addDefaultValues(buildConfiguration);
        buildConfiguration.addProperty(Constants.ENABLED_FOR_JOB, false);
    }
}