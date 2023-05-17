/*
 * Copyright 2018 Atlassian Pty Ltd.
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
import com.atlassian.bamboo.build.CustomPreBuildAction;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryPrometheusPreBuildAction implements CustomPreBuildAction {
    private BuildContext buildContext;
    private final BuildLoggerManager buildLoggerManager;
    static final String RESULT_NODE = "result.isolated.docker.node";

    private static final String KUBE_POD_NAME = System.getenv("KUBE_POD_NAME");
    private static final String STEP_PERIOD = "1000s";
    private static final Logger logger = LoggerFactory.getLogger(QueryPrometheusPreBuildAction.class);

    @Inject
    public QueryPrometheusPreBuildAction(BuildLoggerManager buildLoggerManager) {
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
    @NotNull
    public BuildContext call() {
        Configuration config = AccessConfiguration.forContext(buildContext);

        if (config.isEnabled()) {
            BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());

            String prometheusUrl =
                    buildContext.getBuildResult().getCustomBuildData().get(GlobalConfiguration.BANDANA_PROMETHEUS_URL);
            if (StringUtils.isBlank(prometheusUrl)) {
                buildLogger.addErrorLogEntry("Prometheus URL not configured on server.");
                return buildContext;
            }

            buildLogger.addBuildLogEntry("Name of pod: " + KUBE_POD_NAME);
            loadImageDetails(prometheusUrl, "kube_pod_container_info{pod=\"" + KUBE_POD_NAME + "\"}", buildLogger);
            loadHost(prometheusUrl, "kube_pod_info{pod=\"" + KUBE_POD_NAME + "\"}", buildLogger, buildContext);
        }
        return buildContext;
    }

    private JSONObject loadJson(String prometheusUrl, String query) throws URISyntaxException, IOException {
        long now = Instant.now().getEpochSecond();
        return QueryPrometheus.query(prometheusUrl, query, STEP_PERIOD, now - 1000, now);
    }

    private void loadImageDetails(String prometheusUrl, String query, BuildLogger buildLogger) {
        try {
            JSONObject jsonResponse = loadJson(prometheusUrl, query);
            JSONArray result = jsonResponse.getJSONObject("data").getJSONArray("result");
            if (result.length() == 0) {
                buildLogger.addBuildLogEntry(
                        "Unable to load image details, as no metrics were found for this pod."
                                + " This can occur when the build time is too short for metrics to appear in Prometheus or there was a delay in processing them.");
                return;
            }
            Set<String> processed = new HashSet<>();
            for (int i = 0; i < result.length(); i++) {
                JSONObject metric = result.getJSONObject(i).getJSONObject("metric");
                String container = metric.getString("container");
                String image = metric.getString("image");
                String imageId = metric.optString("image_id");

                if (processed.add(container)) {
                    if (StringUtils.isNotBlank(imageId)) {
                        buildLogger.addBuildLogEntry(
                                String.format("Container '%s' used image '%s'", container, imageId));
                    } else {
                        buildLogger.addBuildLogEntry(
                                String.format("Container '%s' used image '%s'. SHA not available.", container, image));
                        logger.warn(
                                "image_id for container {} in pod {} was blank. Logging Prometheus JSON: "
                                        + jsonResponse,
                                container,
                                KUBE_POD_NAME);
                    }
                }
            }
        } catch (URISyntaxException | IOException | RuntimeException ex) {
            buildLogger.addErrorLogEntry(String.format(
                    "Error when querying Prometheus server: %s. Query: %s Response %s",
                    prometheusUrl, query, ex.getClass().getName() + " " + ex.getMessage()));
        }
    }

    private void loadHost(String prometheusUrl, String query, BuildLogger buildLogger, BuildContext buildContext) {
        try {
            JSONObject jsonResponse = loadJson(prometheusUrl, query);
            JSONArray result = jsonResponse.getJSONObject("data").getJSONArray("result");
            if (result.length() == 0) {
                buildLogger.addBuildLogEntry(
                        "Unable to load Kube node details, as no metrics were found for this pod."
                                + " This can occur when the build time is too short for metrics to appear in Prometheus or there was a delay in processing them.");
                return;
            }
            JSONObject metric = result.getJSONObject(0).getJSONObject("metric");
            String node = metric.optString("node");
            if (node != null) {
                buildLogger.addBuildLogEntry("Pod running on node '" + node + "'");
                buildContext.getCurrentResult().getCustomBuildData().put(RESULT_NODE, node);
            }
        } catch (URISyntaxException | IOException | RuntimeException ex) {
            buildLogger.addErrorLogEntry(String.format(
                    "Error when querying Prometheus server: %s. Query: %s Response %s",
                    prometheusUrl, query, ex.getClass().getName() + " " + ex.getMessage()));
        }
    }
}
