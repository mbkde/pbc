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

package com.atlassian.buildeng.metrics.shared;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomBuildProcessor;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import org.jetbrains.annotations.NotNull;

/**
 * Base metrics build processor.
 */
public abstract class MetricsBuildProcessor  implements CustomBuildProcessor {
    protected static final String RESULT_PREFIX = "result.isolated.docker.";

    protected final BuildLoggerManager buildLoggerManager;
    protected BuildContext buildContext;
    protected final ArtifactManager artifactManager;


    protected MetricsBuildProcessor(BuildLoggerManager buildLoggerManager, ArtifactManager artifactManager) {
        this.buildLoggerManager = buildLoggerManager;
        this.artifactManager = artifactManager;
    }

    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() {
        Configuration config = AccessConfiguration.forContext(buildContext);

        if (config.isEnabled()) {
            BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());
            generateMetricsGraphs(buildLogger, config);
        }

        return buildContext;
    }

    protected abstract void generateMetricsGraphs(BuildLogger buildLogger, Configuration config);

}
