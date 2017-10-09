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

package com.atlassian.buildeng.kubernetes.metrics;

import com.atlassian.bamboo.artifact.Artifact;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.artifact.ArtifactHandlerPublishingResult;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContextImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactPublishingResult;
import com.atlassian.bamboo.security.SecureToken;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildContextHelper;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor;
import com.atlassian.buildeng.metrics.shared.PreJobActionImpl;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.base.Joiner;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * After the build extracts metrics by calling Prometheus server and generates the
 * a metrics file, uploading them as artifacts.
 */
public class KubernetesMetricsBuildProcessor extends MetricsBuildProcessor {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesMetricsBuildProcessor.class);

    private static final String NAME = "name";
    private static final String METRICS_FOLDER = ".pbc-metrics";

    private KubernetesMetricsBuildProcessor(BuildLoggerManager buildLoggerManager, ArtifactManager artifactManager) {
        super(buildLoggerManager, artifactManager);
    }

    private void publishMetrics(
            String name, SecureToken secureToken, BuildLogger buildLogger, File buildWorkingDirectory,
            final Map<String, String> artifactHandlerConfiguration, BuildContext buildContext) {
        ArtifactDefinitionContextImpl artifact = new ArtifactDefinitionContextImpl("pbc-metrics-" + name, false, secureToken);
        artifact.setCopyPattern(name + ".png");
        artifact.setLocation(".pbc-metrics");
        final ArtifactPublishingResult publishingResult =
                artifactManager.publish(buildLogger,
                        buildContext.getPlanResultKey(),
                        buildWorkingDirectory,
                        artifact,
                        artifactHandlerConfiguration,
                        0);
        buildContext.getCurrentResult().getCustomBuildData().put("image_artifacts_type",
                publishingResult.getSuccessfulPublishingResults()
                        .stream()
                        .findAny()
                        .map((ArtifactHandlerPublishingResult t) -> t.getArtifactHandlerKey())
                        .orElse(Artifact.SYSTEM_LINK_TYPE));
        buildLogger.addBuildLogEntry("Generated and published '" + name + "' container performance image.");
    }

    @Override
    protected void generateMetricsGraphs(BuildLogger buildLogger, Configuration config) {
        // sum(container_cpu_usage_seconds_total{namespace='buildeng',pod_name='puppetci-pco1xp1-amiburn04-1-03449671-3300-45ee-9759-d0e0f7b0672d',container_name='bamboo-agent'})
        String token = buildContext.getCurrentResult().getCustomBuildData().remove(PreJobActionImpl.SECURE_TOKEN);
        String podName = buildContext.getCurrentResult().getCustomBuildData().get(RESULT_PREFIX + NAME);
        if (podName != null && token != null) {
            final Map<String, String> artifactHandlerConfiguration = BuildContextHelper
                    .getArtifactHandlerConfiguration(buildContext);
            Path buildWorkingDirectory = BuildContextHelper.getBuildWorkingDirectory((CommonContext)buildContext)
                    .toPath();
            final SecureToken secureToken = SecureToken.createFromString(token);

            List<String> names = new ArrayList<>();
            for (String container : config.getExtraContainers()
                    .stream()
                    .map(Configuration.ExtraContainer::getName)
                    .collect(Collectors.toList())) {
                Path targetDir = buildWorkingDirectory.resolve(METRICS_FOLDER);
                String cpuName = container + "-cpu";
                String memoryName = container + "-memory";
                generateCpuMetricsFile(targetDir.resolve(cpuName), buildLogger);
                generateMemoryMetricsFile(targetDir.resolve(memoryName), buildLogger);
                publishMetrics(cpuName, secureToken, buildLogger, buildWorkingDirectory.toFile(),
                        artifactHandlerConfiguration, buildContext);
                publishMetrics(memoryName, secureToken, buildLogger, buildWorkingDirectory.toFile(),
                        artifactHandlerConfiguration, buildContext);
                names.add("pbc-metrics-" + cpuName);
                names.add("pbc-metrics-" + memoryName);
            }
            buildContext.getCurrentResult().getCustomBuildData().put("image_artifacts", Joiner.on(",").join(names));
        }
    }

    private void generateCpuMetricsFile(Path location, BuildLogger buildLogger) {

    }

    private void generateMemoryMetricsFile(Path containerFolder, BuildLogger buildLogger) {

    }
}
