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

import com.atlassian.bamboo.artifact.Artifact;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomBuildProcessor;
import com.atlassian.bamboo.build.artifact.ArtifactHandlerPublishingResult;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContextImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactPublishingResult;
import com.atlassian.bamboo.security.SecureToken;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;

import java.io.File;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Base metrics build processor.
 */
public abstract class MetricsBuildProcessor  implements CustomBuildProcessor {
    protected static final String RESULT_PREFIX = "result.isolated.docker.";
    protected static final String METRICS_FOLDER = ".pbc-metrics";
    protected static final String ARTIFACT_BUILD_DATA_KEY = "metrics_artifacts";
    protected static final String ARTIFACT_TYPE_BUILD_DATA_KEY = "metrics_artifacts_type";

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

    protected void publishMetrics(
            String name, String fileExtension, SecureToken secureToken, BuildLogger buildLogger,
            File buildWorkingDirectory, final Map<String, String> artifactHandlerConfiguration,
            BuildContext buildContext) {
        ArtifactDefinitionContextImpl artifact = new ArtifactDefinitionContextImpl(
                "pbc-metrics-" + name, false, secureToken);
        artifact.setCopyPattern(name + fileExtension);
        artifact.setLocation(METRICS_FOLDER);
        final ArtifactPublishingResult publishingResult =
                artifactManager.publish(buildLogger,
                        buildContext.getPlanResultKey(),
                        buildWorkingDirectory,
                        artifact,
                        artifactHandlerConfiguration,
                        0);
        buildContext.getCurrentResult().getCustomBuildData()
                .put(ARTIFACT_TYPE_BUILD_DATA_KEY, publishingResult.getSuccessfulPublishingResults()
                        .stream()
                        .findAny()
                        .map(ArtifactHandlerPublishingResult::getArtifactHandlerKey).orElse(Artifact.SYSTEM_LINK_TYPE));
        buildLogger.addBuildLogEntry("Generated and published '" + name + "' container performance artifact.");
    }

    protected abstract void generateMetricsGraphs(BuildLogger buildLogger, Configuration config);

}
