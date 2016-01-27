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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomPreBuildAction;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPreBuildActionImpl implements CustomPreBuildAction {

    private BuildContext buildContext;
    private final Logger LOG = LoggerFactory.getLogger(CustomPreBuildActionImpl.class);
    private final BuildLoggerManager buildLoggerManager;

    public CustomPreBuildActionImpl(BuildLoggerManager buildLoggerManager) {
        this.buildLoggerManager = buildLoggerManager;
    }

    @Override
    public ErrorCollection validate(BuildConfiguration config) {
        return new SimpleErrorCollection();
    }

    @Override
    public void init(BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @Override
    public BuildContext call() throws InterruptedException, Exception {
        Configuration config = Configuration.forBuildContext(buildContext);
        if (config.isEnabled()) {
            String longr = buildContext.getBuildResult().getCustomBuildData().get(Constants.RESULT_TIME_QUEUED);
            long start = Long.parseLong(longr != null ? longr : "0");
            final BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());
            buildLogger.addBuildLogEntry("Docker image "  + config.getDockerImage() + " took:" + (System.currentTimeMillis() - start) + " ms to start building.");
        }
        return buildContext;
    }

}
