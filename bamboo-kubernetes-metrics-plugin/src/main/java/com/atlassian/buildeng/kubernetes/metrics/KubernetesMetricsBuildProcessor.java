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

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.glassfish.jersey.logging.LoggingFeature;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;


/**
 * After the build extracts metrics by calling Prometheus server and generates the
 * a metrics file, uploading them as artifacts.
 */
public class KubernetesMetricsBuildProcessor extends MetricsBuildProcessor {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesMetricsBuildProcessor.class);

    private static final String NAME = "name";
    private static final long SUBMIT_TIMESTAMP = Integer.parseInt(System.getenv("SUBMIT_TIMESTAMP"));
    private static final String KUBE_POD_NAME = System.getenv("KUBE_POD_NAME");
    private static final String PROPERTY_PROMETHEUS_HTTP_API_SERVER = "pbc.metrics.prometheus.url";
    private static final String METRICS_FOLDER = ".pbc-metrics";
    static final String ARTIFACT_BUILD_DATA_KEY = "metrics_artifacts";

    private KubernetesMetricsBuildProcessor(BuildLoggerManager buildLoggerManager, ArtifactManager artifactManager) {
        super(buildLoggerManager, artifactManager);
    }

    private void publishMetrics(
            String name, SecureToken secureToken, BuildLogger buildLogger, File buildWorkingDirectory,
            final Map<String, String> artifactHandlerConfiguration, BuildContext buildContext) {
    }

    @Override
    protected void generateMetricsGraphs(BuildLogger buildLogger, Configuration config) {
        String token = buildContext.getCurrentResult().getCustomBuildData().remove(PreJobActionImpl.SECURE_TOKEN);
        if (KUBE_POD_NAME != null && token != null) {
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
                generateCpuMetricsFile(targetDir.resolve(cpuName), container, buildLogger);
                generateMemoryMetricsFile(targetDir.resolve(memoryName), container, buildLogger);
                publishMetrics(cpuName, secureToken, buildLogger, buildWorkingDirectory.toFile(),
                        artifactHandlerConfiguration, buildContext);
                publishMetrics(memoryName, secureToken, buildLogger, buildWorkingDirectory.toFile(),
                        artifactHandlerConfiguration, buildContext);
                names.add("pbc-metrics-" + cpuName);
                names.add("pbc-metrics-" + memoryName);
            }
            buildContext.getCurrentResult().getCustomBuildData()
                    .put(ARTIFACT_BUILD_DATA_KEY, Joiner.on(",").join(names));
        }
    }

    private void generateCpuMetricsFile(Path location, String containerName, BuildLogger buildLogger) {
        Client client = ClientBuilder
                .newBuilder()
                .property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_TEXT)
                .build();
        WebTarget webTarget = client
                .target(System.getProperty(PROPERTY_PROMETHEUS_HTTP_API_SERVER))
                .path("api/v1/query_range")
                .queryParam("query",
                        String.format("container_memory_usage_bytes{pod_name=\"%s\",container_name=\"%s\"}",
                                KUBE_POD_NAME, containerName))
                .queryParam("step", "15s")
                .queryParam("start", SUBMIT_TIMESTAMP)
                .queryParam("end", Instant.now().getEpochSecond());
        JSONObject response = webTarget.request(MediaType.APPLICATION_JSON).get(JSONObject.class);
//        Files.write(location, data);
    }

    private void generateMemoryMetricsFile(Path containerFolder, String containerName, BuildLogger buildLogger) {

    }
}
