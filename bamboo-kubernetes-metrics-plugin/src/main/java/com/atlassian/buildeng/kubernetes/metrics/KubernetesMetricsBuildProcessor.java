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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.codehaus.plexus.util.StringUtils;
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
    private static final String PROMETHEUS_FS_WRITE = "container_fs_writes_bytes_total";
    private static final String PROMETHEUS_FS_READ = "container_fs_reads_bytes_total";
    private static final String PROMETHEUS_NET_READ = "container_network_receive_bytes_total";
    private static final String PROMETHEUS_NET_WRITE = "container_network_transmit_bytes_total";

    private static final String KUBE_POD_NAME = System.getenv("KUBE_POD_NAME");
    private static final String SUBMIT_TIMESTAMP = System.getenv("SUBMIT_TIMESTAMP");
    private static final String STEP_PERIOD = "15s";

    private KubernetesMetricsBuildProcessor(BuildLoggerManager buildLoggerManager,
            ArtifactManager artifactManager) {
        super(buildLoggerManager, artifactManager);
    }

    @Override
    protected void generateMetricsGraphs(BuildLogger buildLogger, Configuration config, BuildContext context) {
        if (KUBE_POD_NAME != null) {
            if (SUBMIT_TIMESTAMP == null) {
                buildLogger.addBuildLogEntry("No SUBMIT_TIMESTAMP environment variable found in custom build data.");
                return;
            }
            String token = buildContext.getCurrentResult().getCustomBuildData().remove(PreJobActionImpl.SECURE_TOKEN);
            if (token == null) {
                //TODO eventually remove, it's only here to allow rolling upgrade after renaming the constant.
                token = buildContext.getCurrentResult().getCustomBuildData().remove("secureToken");
                if (token == null) {
                    buildLogger.addBuildLogEntry("No SecureToken found in custom build data.");
                    return;
                }
            }
            String prometheusUrl = buildContext.getBuildResult()
                    .getCustomBuildData().get(GlobalConfiguration.BANDANA_PROMETHEUS_URL);
            if (StringUtils.isBlank(prometheusUrl)) {
                buildLogger.addBuildLogEntry("Prometheus URL not configured on server.");
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
                    Stream.of(createReservationSize("bamboo-agent", context)),
                    config.getExtraContainers().stream().map(
                        (Configuration.ExtraContainer e) -> createReservationSize(e.getName(), context)))
                    .collect(Collectors.toList());

            //not specific to container
            collectMetric(PROMETHEUS_NET_WRITE, "net-write",
                    "sum(irate(%s{pod_name=\"%s\"}[1m]))",
                    "", buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);
            collectMetric(PROMETHEUS_NET_READ, "net-read",
                    "sum(irate(%s{pod_name=\"%s\"}[1m]))",
                    "", buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);

            for (ReservationSize containerPair : containers) {
                String container = containerPair.name;

                final Datapoint[] memAll = collectMemoryMetric(PROMETHEUS_MEMORY_METRIC, "-memory",
                        container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);
                final Datapoint[] memCache = collectMemoryMetric(PROMETHEUS_MEMORY_CACHE_METRIC, "-memory-cache",
                        container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);
                final Datapoint[] memRss = collectMemoryMetric(PROMETHEUS_MEMORY_RSS_METRIC, "-memory-rss",
                        container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);
                final Datapoint[] memSwap = collectMemoryMetric(PROMETHEUS_MEMORY_SWAP_METRIC, "-memory-swap",
                        container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);

                collectCpuMetric(PROMETHEUS_CPU_METRIC, "-cpu", container, buildLogger,
                        secureToken, prometheusUrl, buildWorkingDirectory);
                collectCpuMetric(PROMETHEUS_CPU_USER_METRIC, "-cpu-user", container, buildLogger,
                        secureToken, prometheusUrl, buildWorkingDirectory);
                collectCpuMetric(PROMETHEUS_CPU_SYSTEM_METRIC, "-cpu-system", container, buildLogger,
                        secureToken, prometheusUrl, buildWorkingDirectory);
                collectMetric(PROMETHEUS_FS_WRITE, "-fs-write",
                        "sum(irate(%s{pod_name=\"%s\",container_name=\"%s\"}[1m]))",
                        container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);
                collectMetric(PROMETHEUS_FS_READ, "-fs-read",
                        "sum(irate(%s{pod_name=\"%s\",container_name=\"%s\"}[1m]))",
                        container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);

                artifactsJsonDetails.put(generateArtifactDetailsJson(containerPair));

                logValues(memAll, memRss, memCache, memSwap, containerPair, buildLogger);
            }

            buildContext.getCurrentResult().getCustomBuildData()
                    .put(KubernetesViewMetricsAction.ARTIFACT_BUILD_DATA_KEY, artifactsJsonDetails.toString());
        }
    }

    private void collectCpuMetric(String metricName, String suffix, String container,
        BuildLogger buildLogger, SecureToken secureToken, String prometheusUrl, Path buildWorkingDirectory) {
        collectMetric(metricName, suffix, "sum(irate(%s{pod_name=\"%s\",container_name=\"%s\"}[1m]))",
            container, buildLogger, secureToken, prometheusUrl, buildWorkingDirectory);
    }

    private void collectMetric(String metricName, String suffix, String query, String container,
            BuildLogger buildLogger, SecureToken secureToken, String prometheusUrl, Path buildWorkingDirectory) {
        String fileName = container + suffix;
        String queryMemory = String.format(query,
                metricName, KUBE_POD_NAME, container);
        Datapoint[] dp = generateMetricsFile(buildWorkingDirectory.resolve(METRICS_FOLDER).resolve(fileName + ".json"),
                queryMemory, container, prometheusUrl, buildLogger);

        if(dp.length != 0){ // Metric file exists
            publishMetrics(fileName, ".json", secureToken, buildLogger, buildWorkingDirectory.toFile(),
                    BuildContextHelper.getArtifactHandlerConfiguration(buildContext), buildContext);
        }
    }


    private Datapoint[] collectMemoryMetric(String metricName, String suffix, String container,
            BuildLogger buildLogger, SecureToken secureToken, String prometheusUrl, Path buildWorkingDirectory) {
        String fileName = container + suffix;
        String queryMemory = String.format("%s{pod_name=\"%s\",container_name=\"%s\"}",
                metricName, KUBE_POD_NAME, container);
        Datapoint[] dp = generateMetricsFile(buildWorkingDirectory.resolve(METRICS_FOLDER).resolve(fileName + ".json"),
                queryMemory, container, prometheusUrl, buildLogger);
        if(dp.length != 0){ // Metric file exists
            publishMetrics(fileName, ".json", secureToken, buildLogger, buildWorkingDirectory.toFile(),
                    BuildContextHelper.getArtifactHandlerConfiguration(buildContext), buildContext);
        }
        return dp;
    }

    /**
     * Create a JSON file containing the metrics by querying Prometheus and massaging its output.
     * Prometheus HTTP API: https://prometheus.io/docs/querying/api/
     */

    @Nonnull
    private Datapoint[] generateMetricsFile(Path location, String query,
            String containerName, String prometheusUrl, BuildLogger buildLogger) {
        long submitTimestamp = Long.parseLong(SUBMIT_TIMESTAMP) / 1000;
        try {
            URI uri = new URIBuilder(prometheusUrl)
                    .setPath("api/v1/query_range")
                    .setParameter("query", query)
                    .setParameter("step", STEP_PERIOD)
                    .setParameter("start", Long.toString(submitTimestamp))
                    .setParameter("end", Long.toString(Instant.now().getEpochSecond()))
                    .build();
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Charset", "UTF-8");
            connection.setConnectTimeout((int)Duration.ofSeconds(10).toMillis());
            connection.setReadTimeout((int)Duration.ofSeconds(60).toMillis());

            String response;
            try {
                response = IOUtils.toString(connection.getInputStream(), "UTF-8");
            } finally {
                connection.disconnect();
            }
            JSONObject jsonResponse = new JSONObject(response);
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
                buildLogger.addBuildLogEntry(
                        String.format("Error when attempting to write metrics file to %s", location));
                return new Datapoint[0];
            }
        } catch (URISyntaxException | IOException | RuntimeException ex) {
            logger.warn(String.format("Error when querying Prometheus server, metric won't be published:"
                            + " %s. Query: %s Response %s",
                            prometheusUrl, query, ex.getClass().getName() + " " + ex.getMessage()));
            return new Datapoint[0];
        }
    }

    private void logValues(Datapoint[] memAll, Datapoint[] memRss, Datapoint[] memCache, Datapoint[] memSwap,
            ReservationSize container, BuildLogger buildLogger) {
        double maxRss = maxValue(memRss).orElse(-1);
        double maxCache = maxValue(memCache).orElse(-1);
        double maxSwap = maxValue(memSwap).orElse(-1);

        logger.info("max_swap:" + (long)maxSwap + " container:" + container.name + " pod:" + KUBE_POD_NAME);
        logger.info("max_cache:" + (long)maxCache + " container:" + container.name + " pod:" + KUBE_POD_NAME);
        logger.info("max_rss:" + (long)maxRss + " container:" + container.name + " pod:" + KUBE_POD_NAME);
        Datapoint maxoverall = maxValueKey(memAll);
        if (maxoverall != null) {
            logger.info("max_total:" + (long)maxoverall.y + " container:" + container.name + " pod:" + KUBE_POD_NAME);
            double rss = Arrays.stream(memRss)
                    .filter((Datapoint t) -> t.x == maxoverall.x)
                    .findFirst().orElse(Datapoint.NONE).y;
            double swap = Arrays.stream(memSwap)
                    .filter((Datapoint t) -> t.x == maxoverall.x)
                    .findFirst().orElse(Datapoint.NONE).y;
            if (maxoverall.y > container.memoryInBytes) {
                if (rss > container.memoryInBytes) {
                    buildLogger.addBuildLogEntry("Warning: The container "
                            + container.name + " is using more memory than it reserved."
                                    + " Please adjust the size of the container.");
                }
            }
            if (maxoverall.y < container.memoryInBytes / 4) {
                buildLogger.addBuildLogEntry("The container "
                            + container.name + " is using less than quarter of the memory reserved."
                                    + " Please adjust the size of the container.");
            }
            //TODO max of swap at maxoverall or it's own maximum via maxValueKey(memSwap) or both?
            Datapoint dpSwap = maxValueKey(memSwap);
            logAdditionalChecks(container.name, container.memoryInBytes, (long)maxoverall.y, (long)rss,
                    Math.max(dpSwap != null ? (long)dpSwap.y : 0, (long)swap));
        }
    }

    private Datapoint maxValueKey(Datapoint[] arr) {
        return Arrays.stream(arr).max(Comparator.comparingDouble((Datapoint o) -> o.y)).orElse(null);
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

    private ReservationSize createReservationSize(String name, BuildContext context) {
        Map<String, String> cc = context.getBuildResult().getCustomBuildData();
        String cpuReq = cc.getOrDefault(Configuration.DOCKER_IMAGE_DETAIL + "." + name + ".cpu", "0");
        String memReq = cc.getOrDefault(Configuration.DOCKER_IMAGE_DETAIL + "." + name + ".memory", "0");
        String memLimitReq = cc.getOrDefault(Configuration.DOCKER_IMAGE_DETAIL + "." + name + ".memoryLimit", "0");
        int cpuRequest = Integer.parseInt(cpuReq);
        int memoryRequest = Integer.parseInt(memReq);
        int memoryLimit = Integer.parseInt(memLimitReq);
        return new ReservationSize(name, cpuRequest, memoryRequest, memoryLimit);
    }

    private JSONObject generateArtifactDetailsJson(ReservationSize reservation) {
        JSONObject artifactDetails = new JSONObject();
        artifactDetails.put("name", reservation.name);
        artifactDetails.put("cpuRequest", reservation.cpu);
        artifactDetails.put("memoryRequest", reservation.memory);
        artifactDetails.put("memoryLimit", reservation.memoryLimit);
        return artifactDetails;
    }

    protected void logAdditionalChecks(String containerName, long reservedMemoryInBytes,
            long usedMaximum, long usedMaxRss, long usedMaxSwap) {
        //do nothing intentionally.
        logger.debug("in logAdditonalChecks");
    }

    public static class Datapoint {
        private final int x;
        private final double y;
        private static Datapoint NONE = new Datapoint(0, -1);

        public Datapoint(int x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class ReservationSize {
        private final String name;
        private final int cpu;
        private final int memory;
        private final long memoryInBytes;
        private final int memoryLimit;
        private final long memoryLimitInBytes;

        ReservationSize(String name, int cpu, int memory, int memoryLimit) {
            this.name = name;
            this.cpu = cpu;
            this.memory = memory;
            this.memoryInBytes = (long)memory * 1000000;
            this.memoryLimit = memoryLimit;
            this.memoryLimitInBytes = (long)memoryLimit * 1000000;
        }

    }

}
