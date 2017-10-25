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
import com.atlassian.bamboo.build.artifact.ArtifactFileData;
import com.atlassian.bamboo.build.artifact.ArtifactLinkDataProvider;
import com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor;
import com.atlassian.buildeng.metrics.shared.ViewMetricsAction;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.logging.LoggingFeature;



public class KubernetesViewMetricsAction extends ViewMetricsAction {
    static final String ARTIFACT_BUILD_DATA_KEY = "kubernetes_metrics_artifacts";

    private HashMap<String, String> cpuMap = new HashMap<>();
    private HashMap<String, String> memoryMap = new HashMap<>();
    private List<String> containerList = new ArrayList<>();

    public List<String> getContainerList() {
        return containerList;
    }

    public Map<String, String> getCpuMap() {
        return cpuMap;
    }

    public Map<String, String> getMemoryMap() {
        return memoryMap;
    }

    @Override
    public void prepare() throws Exception {
        String artifactNames = resultsSummary.getCustomBuildData().get(ARTIFACT_BUILD_DATA_KEY);
        if (artifactNames != null) {
            Splitter.on(",").splitToList(artifactNames).forEach((String artifactName) -> {
                String containerName = artifactName
                        .replaceAll("^" + Pattern.quote(MetricsBuildProcessor.ARTIFACT_PREFIX), "")
                        .replaceAll("(-cpu|-memory)$", "");
                Artifact artifact = createArtifact(
                        artifactName, resultsSummary.getPlanResultKey(),
                        resultsSummary.getCustomBuildData().get(MetricsBuildProcessor.ARTIFACT_TYPE_BUILD_DATA_KEY));
                ArtifactLinkDataProvider artifactLinkDataProvider = artifactLinkManager.getArtifactLinkDataProvider(
                        artifact);
                if (artifactLinkDataProvider == null) {
                    addActionError("Unable to find artifact link data provider for artifact link");
                    return;
                }
                final String sanitisedTag = "";
                Iterable<ArtifactFileData> artifactFiles = artifactLinkDataProvider.listObjects(sanitisedTag);
                ArtifactFileData single = getSingleDownloadableFile(artifactFiles);
                if (single != null) {
                    if (!containerList.contains(containerName)) {
                        containerList.add(containerName);
                    }
                    Client client = ClientBuilder
                            .newBuilder()
                            .property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_CLIENT,
                                    LoggingFeature.Verbosity.PAYLOAD_TEXT)
                            .build();

                    WebTarget webTarget = client.target(single.getUrl());

                    // We have to directly retrieve the artifact here instead of passing the URL to the user due to
                    // same-origin policy.
                    Response response = webTarget.request(MediaType.APPLICATION_JSON).get();
                    if (response.getStatusInfo().getFamily().compareTo(Response.Status.Family.SUCCESSFUL) != 0) {
                        addActionError(
                                String.format("Error retrieving metrics JSON artifact from %s", single.getUrl()));
                        return;
                    }

                    if (artifactName.endsWith("-cpu")) {
                        cpuMap.put(containerName, response.readEntity(String.class));
                    } else if (artifactName.endsWith("-memory")) {
                        memoryMap.put(containerName, response.readEntity(String.class));
                    }
                }
            });
        }

    }
}
