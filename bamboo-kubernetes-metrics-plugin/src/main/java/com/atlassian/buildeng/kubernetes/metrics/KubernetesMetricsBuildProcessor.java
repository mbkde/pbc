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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.logging.LoggingFeature;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * After the build extracts metrics by calling Prometheus server and generates the
 * a metrics file, uploading them as artifacts.
 */
public class KubernetesMetricsBuildProcessor extends MetricsBuildProcessor {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesMetricsBuildProcessor.class);

    private static final String PROMETHEUS_MEMORY_METRIC = "container_memory_usage_bytes";
    private static final String PROMETHEUS_CPU_METRIC = "container_cpu_usage_seconds_total";
    private static final long SUBMIT_TIMESTAMP = Integer.parseInt(System.getenv("SUBMIT_TIMESTAMP"));
    private static final String KUBE_POD_NAME = System.getenv("KUBE_POD_NAME");
    private static final String PROMETHEUS_SERVER = System.getProperty("pbc.metrics.prometheus.url");
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

                generateMetricsFile(targetDir.resolve(cpuName), PROMETHEUS_CPU_METRIC, container, buildLogger);
                generateMetricsFile(targetDir.resolve(memoryName), PROMETHEUS_MEMORY_METRIC, container, buildLogger);

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

    private void generateMetricsFile(Path location, String metric, String containerName, BuildLogger buildLogger) {
        Client client = ClientBuilder
                .newBuilder()
                .property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_TEXT)
                .build();
        WebTarget webTarget = client
                .target(System.getProperty(PROMETHEUS_SERVER))
                .path("api/v1/query_range")
                .queryParam("query",
                        String.format("%s{pod_name=\"%s\",container_name=\"%s\"}",
                                metric, KUBE_POD_NAME, containerName))
                .queryParam("step", "15s")
                .queryParam("start", SUBMIT_TIMESTAMP)
                .queryParam("end", Instant.now().getEpochSecond());
        Response response = webTarget.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatusInfo().getFamily().compareTo(Response.Status.Family.SUCCESSFUL) != 0) {
            buildLogger.addErrorLogEntry(
                    String.format("Error when querying Prometheus server: %s. Response %s",
                            PROMETHEUS_SERVER, response.readEntity(String.class)));
            return;
        }
        JSONObject jsonResponse = response.readEntity(JSONObject.class);
        JSONArray values = jsonResponse
                .getJSONObject("data")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONArray("values");

        try {
            Files.write(location, createJsonArtifact(values).getBytes());
        } catch (IOException e) {
            buildLogger.addErrorLogEntry(String.format("Error when attempting to write metrics file to %s", location));
        }
    }

    /**
     * This massages the values obtained from Prometheus into the format that Rickshaw.js expects.
     */
    private String createJsonArtifact(JSONArray values) {
        JSONArray metrics = new JSONArray();
        JSONObject series = new JSONObject();
        JSONArray data = new JSONArray();
        series.put("data", data);
        metrics.put(series);

        for (int i = 0; i < values.length(); i++) {
            JSONArray value = values.getJSONArray(i);
            data.put(createDataPoint(value.getInt(0), value.getInt(1)));
        }
        return metrics.toString();
    }

    private JSONObject createDataPoint(int x, int y) {
        JSONObject point = new JSONObject();
        point.put("x", x);
        point.put("y", y);
        return point;
    }

}
