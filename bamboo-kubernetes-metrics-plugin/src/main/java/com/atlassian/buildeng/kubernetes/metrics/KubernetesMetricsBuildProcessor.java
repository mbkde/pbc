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
import com.atlassian.bamboo.v2.build.BuildContextHelper;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor;
import com.atlassian.buildeng.metrics.shared.PreJobActionImpl;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
    private static final String KUBE_POD_NAME = System.getenv("KUBE_POD_NAME");
    private static final String SUBMIT_TIMESTAMP = System.getenv("SUBMIT_TIMESTAMP");
    // TODO: Pull this from somewhere
    private static final String PROMETHEUS_SERVER = "http://prometheus.monitoring.svc.cluster.local:9090";
    private static final String STEP_PERIOD = "15s";

    private KubernetesMetricsBuildProcessor(BuildLoggerManager buildLoggerManager, ArtifactManager artifactManager) {
        super(buildLoggerManager, artifactManager);
    }

    @Override
    protected void generateMetricsGraphs(BuildLogger buildLogger, Configuration config) {
        if (KUBE_POD_NAME != null) {
            if (SUBMIT_TIMESTAMP == null) {
                buildLogger.addErrorLogEntry("No SUBMIT_TIMESTAMP environment variable found in custom build data.");
                return;
            }
            String token = buildContext.getCurrentResult().getCustomBuildData().remove(PreJobActionImpl.SECURE_TOKEN);
            if (token == null) {
                buildLogger.addErrorLogEntry("No SecureToken found in custom build data.");
                return;
            }
            final Map<String, String> artifactHandlerConfiguration = BuildContextHelper
                    .getArtifactHandlerConfiguration(buildContext);

            Path buildWorkingDirectory = BuildContextHelper.getBuildWorkingDirectory((CommonContext) buildContext)
                    .toPath();
            Path targetDir = buildWorkingDirectory.resolve(METRICS_FOLDER);
            try {
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                buildLogger.addBuildLogEntry("Unable to create metrics folder: " + targetDir);
                return;
            }

            final SecureToken secureToken = SecureToken.createFromString(token);

            JSONArray artifactsJsonDetails = new JSONArray();
            List<Pair<String, Enum>> containers = Stream.concat(
                    config.getExtraContainers().stream().map(
                        (Configuration.ExtraContainer e) ->
                                    new ImmutablePair<>(e.getName(), (Enum) e.getExtraSize())),
                    Stream.of(new ImmutablePair<>("bamboo-agent", (Enum) config.getSize())))
                    .collect(Collectors.toList());
            for (Pair<String, Enum> containerPair : containers) {
                String container = containerPair.getLeft();

                String cpuName = container + "-cpu";
                String memoryName = container + "-memory";

                String queryCpu = String.format("sum(irate(%s{pod_name=\"%s\",container_name=\"%s\"}[1m]))",
                        PROMETHEUS_CPU_METRIC, KUBE_POD_NAME, container);
                generateMetricsFile(
                        targetDir.resolve(cpuName + ".json"), queryCpu, container, buildLogger);

                String queryMemory = String.format("%s{pod_name=\"%s\",container_name=\"%s\"}",
                        PROMETHEUS_MEMORY_METRIC, KUBE_POD_NAME, container);
                generateMetricsFile(
                        targetDir.resolve(memoryName + ".json"), queryMemory, container, buildLogger);

                publishMetrics(cpuName, ".json", secureToken, buildLogger, buildWorkingDirectory.toFile(),
                        artifactHandlerConfiguration, buildContext);
                publishMetrics(memoryName, ".json", secureToken, buildLogger, buildWorkingDirectory.toFile(),
                        artifactHandlerConfiguration, buildContext);

                artifactsJsonDetails.put(generateArtifactDetailsJson(container, containerPair.getRight()));
                
                logValues(container, buildLogger);
                
            }

            buildContext.getCurrentResult().getCustomBuildData()
                    .put(KubernetesViewMetricsAction.ARTIFACT_BUILD_DATA_KEY, artifactsJsonDetails.toString());
        }
    }

    /**
     * Create a JSON file containing the metrics by querying Prometheus and massaging its output.
     * Prometheus HTTP API: https://prometheus.io/docs/querying/api/
     */
    private void generateMetricsFile(Path location, String query, String containerName, BuildLogger buildLogger) {
        long submitTimestamp = Long.parseLong(SUBMIT_TIMESTAMP) / 1000;
        WebTarget webTarget = createClient()
                .target(PROMETHEUS_SERVER)
                .path("api/v1/query_range")
                .queryParam("query", encodeQuery(query))
                .queryParam("step", STEP_PERIOD)
                .queryParam("start", submitTimestamp)
                .queryParam("end", Instant.now().getEpochSecond());

        Response response = webTarget.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatusInfo().getFamily().compareTo(Response.Status.Family.SUCCESSFUL) != 0) {
            buildLogger.addErrorLogEntry(
                    String.format("Error when querying Prometheus server: %s. Query: %s Response %s",
                            PROMETHEUS_SERVER, query, response.readEntity(String.class)));
            return;
        }

        JSONObject jsonResponse = new JSONObject(response.readEntity(String.class));
        JSONArray result = jsonResponse
                .getJSONObject("data")
                .getJSONArray("result");
        if (result.length() == 0) {
            buildLogger.addBuildLogEntry(String.format("No metrics found for the container '%s' found."
                    + " This can occur when the build time is too short for metrics to appear in Prometheus.",
                    containerName));
            return;
        }
        JSONArray values = result.getJSONObject(0).getJSONArray("values");

        try {
            Files.write(location, createJsonArtifact(values).getBytes());
        } catch (IOException e) {
            buildLogger.addErrorLogEntry(String.format("Error when attempting to write metrics file to %s", location));
        }
    }
    
    private void logValues(String container, BuildLogger buildLogger) {
        String queryMaxSwap = String.format(
                "max_over_time(container_memory_swap{pod_name=\"%s\",container_name=\"%s\"}[12h])", 
                KUBE_POD_NAME, container);
        String swap =  extractValueFromJson(query(queryMaxSwap, buildLogger));
        logger.info("max_swap:" + swap + " container:" + container + " pod:" + KUBE_POD_NAME);
        
        String queryMaxCache = String.format(
                "max_over_time(container_memory_cache{pod_name=\"%s\",container_name=\"%s\"}[12h])",
                KUBE_POD_NAME, container);
        String cache = "max_cache:" + extractValueFromJson(query(queryMaxCache, buildLogger)) 
                + " container:" + container + " pod:" + KUBE_POD_NAME;
        logger.info(cache);
        
        String queryMaxRss = String.format(
                "max_over_time(container_memory_rss{pod_name=\"%s\",container_name=\"%s\"}[12h])",
                KUBE_POD_NAME, container);
        String res = "max_rss:" + extractValueFromJson(query(queryMaxRss, buildLogger)) 
                + " container:" + container + " pod:" + KUBE_POD_NAME;
        logger.info(res);
    }
    
    private String extractValueFromJson(String input) {
        JSONObject jsonResponse = new JSONObject(input);
        JSONArray result = jsonResponse
                .getJSONObject("data")
                .getJSONArray("result");
        if (result.length() == 0) {
            return "0";
        }
        return result.getJSONObject(0).getJSONArray("value").getString(1);
    }
    
    private String query(String query, BuildLogger buildLogger) {
        WebTarget webTarget = createClient()
                .target(PROMETHEUS_SERVER)
                .path("api/v1/query")
                .queryParam("query", encodeQuery(query));

        Response response = webTarget.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatusInfo().getFamily().compareTo(Response.Status.Family.SUCCESSFUL) != 0) {
            buildLogger.addErrorLogEntry(
                    String.format("Error when querying Prometheus server: %s. Query: %s Response %s",
                            PROMETHEUS_SERVER, query, response.readEntity(String.class)));
            return null;
        }

        return response.readEntity(String.class);    
    }
    
    /**
     * This massages the values obtained from Prometheus into the format that Rickshaw.js expects.
     */
    private String createJsonArtifact(JSONArray values) {
        JSONArray data = new JSONArray();

        for (int i = 0; i < values.length(); i++) {
            JSONArray value = values.getJSONArray(i);
            data.put(createDataPoint(value.getInt(0), value.getString(1)));
        }
        return data.toString();
    }

    private JSONObject createDataPoint(int x, String y) {
        JSONObject point = new JSONObject();
        point.put("x", x);
        point.put("y", Float.parseFloat(y));
        return point;
    }

    private JSONObject generateArtifactDetailsJson(String name, Enum containerSize) {
        int cpuRequest;
        int memoryRequest;
        if ("bamboo-agent".equals(name)) {
            Configuration.ContainerSize size = (Configuration.ContainerSize) containerSize;
            cpuRequest = size.cpu();
            memoryRequest = size.memory();

        } else {
            Configuration.ExtraContainerSize size = (Configuration.ExtraContainerSize) containerSize;
            cpuRequest = size.cpu();
            memoryRequest = size.memory();
        }

        JSONObject artifactDetails = new JSONObject();
        artifactDetails.put("name", name);
        artifactDetails.put("cpuRequest", cpuRequest);
        artifactDetails.put("memoryRequest", memoryRequest);
        return artifactDetails;
    }
    
    private Client createClient() {
        return ClientBuilder
                .newBuilder()
                .property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT, LoggingFeature.Verbosity.PAYLOAD_TEXT)
                .build();
    }

    private String encodeQuery(String query) {
        try {
            return URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unable to parse Prometheus query string: " + query, e);
        }
    }
}
