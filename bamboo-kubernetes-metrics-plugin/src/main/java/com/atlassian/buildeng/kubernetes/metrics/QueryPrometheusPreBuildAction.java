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
import com.atlassian.bamboo.v2.build.BuildContextHelper;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.codehaus.plexus.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class QueryPrometheusPreBuildAction implements CustomPreBuildAction {
    private BuildContext buildContext;
    private final BuildLoggerManager buildLoggerManager;
    static final String RESULT_NODE = "result.isolated.docker.node";
    
    private static final String KUBE_POD_NAME = System.getenv("KUBE_POD_NAME");
    private static final String STEP_PERIOD = "1000s";

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
    public BuildContext call() throws InterruptedException, Exception {
        Configuration config = AccessConfiguration.forContext(buildContext);

        if (config.isEnabled()) {
            BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());
        
            String prometheusUrl = buildContext.getBuildResult()
                    .getCustomBuildData().get(GlobalConfiguration.BANDANA_PROMETHEUS_URL);
            if (StringUtils.isBlank(prometheusUrl)) {
                buildLogger.addErrorLogEntry("Prometheus URL not configured on server.");
                return buildContext;
            }
            
            Path buildWorkingDirectory = BuildContextHelper.getBuildWorkingDirectory((CommonContext) buildContext)
                    .toPath();
            loadImageDetails(prometheusUrl, "kube_pod_container_info{pod=\"" + KUBE_POD_NAME  + "\"}",
                    buildLogger, buildContext);
            loadHost(prometheusUrl, "kube_pod_info{pod=\"" + KUBE_POD_NAME  + "\"}", buildLogger, buildContext);
            
        }
        return buildContext;
    }
    
    private JSONObject loadJson(String prometheusUrl, String query) throws URISyntaxException, IOException {
        long now = Instant.now().getEpochSecond();
        URI uri = new URIBuilder(prometheusUrl)
                .setPath("api/v1/query_range")
                .setParameter("query", query)
                .setParameter("step", STEP_PERIOD)
                .setParameter("start", Long.toString(now - 1000))
                .setParameter("end", Long.toString(now))
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
        return new JSONObject(response);

    }
    
    private void loadImageDetails(String prometheusUrl, String query, 
            BuildLogger buildLogger, BuildContext buildContext) {
        try {
            JSONObject jsonResponse = loadJson(prometheusUrl, query);
            JSONArray result = jsonResponse
                    .getJSONObject("data")
                    .getJSONArray("result");
            if (result.length() == 0) {
                buildLogger.addBuildLogEntry("No metrics found for pod."
                        + " This can occur when the build time is too short for metrics to appear in Prometheus.");
                return;
            }
            Set<String> processed = new HashSet<>();
            for (int i = 0; i < result.length(); i++) {
                JSONObject metric = result.getJSONObject(i).getJSONObject("metric");
                String container = metric.getString("container");
                String imageid = metric.getString("image_id");
                if (StringUtils.isNotBlank(imageid)) {
                    if (processed.add(query)) {
                        buildLogger.addBuildLogEntry(String.format("Container '%s' used image %s", container, imageid));
                    }
                }
            }
        } catch (URISyntaxException | IOException | RuntimeException ex) {
            buildLogger.addErrorLogEntry(
                    String.format("Error when querying Prometheus server: %s. Query: %s Response %s",
                            prometheusUrl, query, ex.getClass().getName() + " " + ex.getMessage()));
            return;
        }
        
    }

    private void loadHost(String prometheusUrl, String query, BuildLogger buildLogger, BuildContext buildContext) {
        try {
            JSONObject jsonResponse = loadJson(prometheusUrl, query);
            JSONArray result = jsonResponse
                    .getJSONObject("data")
                    .getJSONArray("result");
            if (result.length() == 0) {
                buildLogger.addBuildLogEntry("No metrics found for pod."
                        + " This can occur when the build time is too short for metrics to appear in Prometheus.");
                return;
            }
            JSONObject metric = result.getJSONObject(0).getJSONObject("metric");
            String node = metric.optString("node");
            if (node != null) {
                buildLogger.addBuildLogEntry("Pod running on node '" + node + "'");
                buildContext.getCurrentResult().getCustomBuildData().put(RESULT_NODE, node);
            }
        } catch (URISyntaxException | IOException | RuntimeException ex) {
            buildLogger.addErrorLogEntry(
                    String.format("Error when querying Prometheus server: %s. Query: %s Response %s",
                            prometheusUrl, query, ex.getClass().getName() + " " + ex.getMessage()));
            return;
        }
    }
    
}
