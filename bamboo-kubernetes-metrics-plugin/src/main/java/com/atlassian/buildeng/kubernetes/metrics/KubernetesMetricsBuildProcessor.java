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
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
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
    private static final String PROMETHEUS_MEMORY_RSS_METRIC = "container_memory_rss";
    private static final String PROMETHEUS_MEMORY_CACHE_METRIC = "container_memory_cache";
    private static final String PROMETHEUS_MEMORY_SWAP_METRIC = "container_memory_swap";
    private static final String PROMETHEUS_CPU_METRIC = "container_cpu_usage_seconds_total";
    private static final String PROMETHEUS_CPU_USER_METRIC = "container_cpu_user_seconds_total";
    private static final String PROMETHEUS_CPU_SYSTEM_METRIC = "container_cpu_system_seconds_total";
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
            List<ReservationSize> containers = Stream.concat(
                    Stream.of(createReservationSize("bamboo-agent", (Enum) config.getSize())),
                    config.getExtraContainers().stream().map(
                        (Configuration.ExtraContainer e) ->
                                    createReservationSize(e.getName(), (Enum) e.getExtraSize())))
                    .collect(Collectors.toList());
            for (ReservationSize containerPair : containers) {
                String container = containerPair.name;

                final Datapoint[] memAll = collectMemoryMetric(PROMETHEUS_MEMORY_METRIC, "-memory",
                        container, buildLogger, secureToken, buildWorkingDirectory);
                final Datapoint[] memCache = collectMemoryMetric(PROMETHEUS_MEMORY_CACHE_METRIC, "-memory-cache", 
                        container, buildLogger, secureToken, buildWorkingDirectory);
                final Datapoint[] memRss = collectMemoryMetric(PROMETHEUS_MEMORY_RSS_METRIC, "-memory-rss", 
                        container, buildLogger, secureToken, buildWorkingDirectory);
                final Datapoint[] memSwap = collectMemoryMetric(PROMETHEUS_MEMORY_SWAP_METRIC, "-memory-swap",
                        container, buildLogger, secureToken, buildWorkingDirectory);

                collectCpuMetric(PROMETHEUS_CPU_METRIC, "-cpu", container, buildLogger, 
                        secureToken, buildWorkingDirectory);
                collectCpuMetric(PROMETHEUS_CPU_USER_METRIC, "-cpu-user", container, buildLogger, 
                        secureToken, buildWorkingDirectory);
                collectCpuMetric(PROMETHEUS_CPU_SYSTEM_METRIC, "-cpu-system", container, buildLogger, 
                        secureToken, buildWorkingDirectory);


                artifactsJsonDetails.put(generateArtifactDetailsJson(containerPair));
                
                logValues(memAll, memRss, memCache, memSwap, containerPair, buildLogger);
            }

            buildContext.getCurrentResult().getCustomBuildData()
                    .put(KubernetesViewMetricsAction.ARTIFACT_BUILD_DATA_KEY, artifactsJsonDetails.toString());
        }
    }
    
    private void collectCpuMetric(String metricName, String suffix, String container,
            BuildLogger buildLogger, SecureToken secureToken, Path buildWorkingDirectory) {
        String fileName = container + suffix;
        String queryMemory = String.format("sum(irate(%s{pod_name=\"%s\",container_name=\"%s\"}[1m]))",
                metricName, KUBE_POD_NAME, container);
        generateMetricsFile(buildWorkingDirectory.resolve(METRICS_FOLDER).resolve(fileName + ".json"),
                queryMemory, container, buildLogger);
        publishMetrics(fileName, ".json", secureToken, buildLogger, buildWorkingDirectory.toFile(),
                BuildContextHelper.getArtifactHandlerConfiguration(buildContext), buildContext);
    }
    
    
    private Datapoint[] collectMemoryMetric(String metricName, String suffix, String container,
            BuildLogger buildLogger, SecureToken secureToken, Path buildWorkingDirectory) {
        String fileName = container + suffix;
        String queryMemory = String.format("%s{pod_name=\"%s\",container_name=\"%s\"}",
                metricName, KUBE_POD_NAME, container);
        Datapoint[] dp = generateMetricsFile(buildWorkingDirectory.resolve(METRICS_FOLDER).resolve(fileName + ".json"),
                queryMemory, container, buildLogger);
        publishMetrics(fileName, ".json", secureToken, buildLogger, buildWorkingDirectory.toFile(),
                BuildContextHelper.getArtifactHandlerConfiguration(buildContext), buildContext);
        return dp;
    }

    /**
     * Create a JSON file containing the metrics by querying Prometheus and massaging its output.
     * Prometheus HTTP API: https://prometheus.io/docs/querying/api/
     */
    @Nonnull
    private Datapoint[] generateMetricsFile(Path location, String query, 
            String containerName, BuildLogger buildLogger) {
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
            return new Datapoint[0];
        }

        JSONObject jsonResponse = new JSONObject(response.readEntity(String.class));
        JSONArray result = jsonResponse
                .getJSONObject("data")
                .getJSONArray("result");
        if (result.length() == 0) {
            buildLogger.addBuildLogEntry(String.format("No metrics found for the container '%s' found."
                    + " This can occur when the build time is too short for metrics to appear in Prometheus.",
                    containerName));
            return new Datapoint[0];
        }
        JSONArray values = result.getJSONObject(0).getJSONArray("values");

        try {
            Datapoint[] toRet = createDatapoints(values);
            Files.write(location, createJsonArtifact(toRet).toString().getBytes());
            return toRet;
        } catch (IOException e) {
            buildLogger.addErrorLogEntry(String.format("Error when attempting to write metrics file to %s", location));
            return new Datapoint[0];
        }
    }
    
    private void logValues(Datapoint[] memAll, Datapoint[] memRss, Datapoint[] memCache, Datapoint[] memSwap, 
            ReservationSize container, BuildLogger buildLogger) {
        double maxRss = maxValue(memRss).orElse(-1);
        double maxCache = maxValue(memCache).orElse(-1);
        double maxSwap = maxValue(memSwap).orElse(-1);
        
        logger.info("max_swap:" + maxSwap + " container:" + container.name + " pod:" + KUBE_POD_NAME);
        logger.info("max_cache:" + maxCache + " container:" + container.name + " pod:" + KUBE_POD_NAME);
        logger.info("max_rss:" + maxRss + " container:" + container.name + " pod:" + KUBE_POD_NAME);
        Datapoint maxoverall = maxValueKey(memAll);
        if (maxoverall != null) {
            logger.info("max_total:" + maxoverall.y + " container:" + container + " pod:" + KUBE_POD_NAME);
            if (maxoverall.y > container.memoryInBytes) {
                double rss = Arrays.stream(memRss)
                        .filter((Datapoint t) -> t.x == maxoverall.x)
                        .findFirst().orElse(Datapoint.NONE).y;
                double swap = Arrays.stream(memSwap)
                        .filter((Datapoint t) -> t.x == maxoverall.x)
                        .findFirst().orElse(Datapoint.NONE).y;
                if (rss > container.memoryInBytes && swap > 0) {
                    buildLogger.addBuildLogEntry("Warning: The container "
                            + container.name + " is using both high amount of RSS memory and swap."
                                    + " Please consider adjusting size of the container.");
                }
            }
            if (maxoverall.y < container.memoryInBytes / 4) {
                buildLogger.addBuildLogEntry("The container "
                            + container.name + " is using less than quarter of the memory reserved."
                                    + " Please consider adjusting the size of the container.");
            }
        }
    }
    
    private Datapoint maxValueKey(Datapoint[] arr) {
        return Arrays.stream(arr).max((Datapoint o1, Datapoint o2) -> {
            return Double.compare(o1.y, o2.y);
        }).get();
    }
    
    private OptionalDouble maxValue(Datapoint[] arr) {
        return Arrays.stream(arr).mapToDouble((Datapoint value) -> value.y).max(); 
    }
    
    /**
     * This massages the values obtained from Prometheus into the format that Rickshaw.js expects.
     */
    private Datapoint[] createDatapoints(JSONArray values) {
        Datapoint[] toRet = new Datapoint[values.length()];

        for (int i = 0; i < values.length(); i++) {
            JSONArray value = values.getJSONArray(i);
            toRet[i] = new Datapoint(value.getInt(0), Double.parseDouble(value.getString(1)));
        }
        return toRet;
    }
    
    private JSONArray createJsonArtifact(Datapoint[] datapoints) {
        JSONArray data = new JSONArray();
        for (Datapoint dp : datapoints) {
            data.put(createDataPointJson(dp));
        }
        return data;
    }

    private JSONObject createDataPointJson(Datapoint dp) {
        JSONObject point = new JSONObject();
        point.put("x", dp.x);
        point.put("y", dp.y);
        return point;
    }

    private ReservationSize createReservationSize(String name, Enum containerSize) {
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
        return new ReservationSize(name, cpuRequest, memoryRequest);
    }

    private JSONObject generateArtifactDetailsJson(ReservationSize reservation) {
        JSONObject artifactDetails = new JSONObject();
        artifactDetails.put("name", reservation.name);
        artifactDetails.put("cpuRequest", reservation.cpu);
        artifactDetails.put("memoryRequest", reservation.memory);
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
    
    private static class Datapoint {
        private final int x;
        private final double y;
        private static Datapoint NONE = new Datapoint(0, -1);

        public Datapoint(int x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    
    private static class ReservationSize {
        private final String name;
        private final int cpu;
        private final int memory;
        private final long memoryInBytes;

        public ReservationSize(String container, int cpu, int memory) {
            this.name = container;
            this.cpu = cpu;
            this.memory = memory;
            this.memoryInBytes = (long)memory * 1000000;
        }
        
    }
    
}
