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

import static com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor.ARTIFACT_PREFIX;

import com.atlassian.bamboo.artifact.Artifact;
import com.atlassian.bamboo.build.artifact.ArtifactFileData;
import com.atlassian.bamboo.build.artifact.ArtifactLinkDataProvider;
import com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor;
import com.atlassian.buildeng.metrics.shared.ViewMetricsAction;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.logging.LoggingFeature;
import org.json.JSONArray;
import org.json.JSONObject;


public class KubernetesViewMetricsAction extends ViewMetricsAction {
    
    public final class ContainerMetrics {
        private final String containerName;
        private String cpuMetrics;
        private String memoryMetrics;
        private String memoryRssMetrics;
        private String memoryCacheMetrics;
        private String memorySwapMetrics;
        private String fsWriteMetrics;
        private String fsReadMetrics;
        private final int memoryLimit;
        private final int cpuLimit;

        ContainerMetrics(String containerName, int cpuLimit, int memoryLimit) {
            this.containerName = containerName;
            this.cpuLimit = cpuLimit;
            this.memoryLimit = memoryLimit;
        }

        public String getName() {
            return containerName;
        }

        public String getCpuMetrics() {
            return cpuMetrics;
        }

        public void setCpuMetrics(String cpuMetrics) {
            this.cpuMetrics = cpuMetrics;
        }

        public String getMemoryMetrics() {
            return memoryMetrics;
        }

        public void setMemoryMetrics(String memoryMetrics) {
            this.memoryMetrics = memoryMetrics;
        }

        public String getMemoryRssMetrics() {
            return memoryRssMetrics;
        }

        public void setMemoryRssMetrics(String memoryRssMetrics) {
            this.memoryRssMetrics = memoryRssMetrics;
        }

        public String getMemoryCacheMetrics() {
            return memoryCacheMetrics;
        }

        public void setMemoryCacheMetrics(String memoryCacheMetrics) {
            this.memoryCacheMetrics = memoryCacheMetrics;
        }

        public String getMemorySwapMetrics() {
            return memorySwapMetrics;
        }

        public void setMemorySwapMetrics(String memorySwapMetrics) {
            this.memorySwapMetrics = memorySwapMetrics;
        }
        
        public int getCpuLimit() {
            return cpuLimit;
        }

        public int getMemoryLimit() {
            return memoryLimit;
        }

        public String getFsWriteMetrics() {
            return fsWriteMetrics;
        }

        public void setFsWriteMetrics(String fsWrite) {
            this.fsWriteMetrics = fsWrite;
        }

        public String getFsReadMetrics() {
            return fsReadMetrics;
        }

        public void setFsReadMetrics(String fsRead) {
            this.fsReadMetrics = fsRead;
        }

        
    }

    static final String ARTIFACT_BUILD_DATA_KEY = "kubernetes_metrics_artifacts";

    private final List<ContainerMetrics> containerList = new ArrayList<>();
    
    private String netWriteMetrics;
    private String netReadMetrics;
    

    public List<ContainerMetrics> getContainerList() {
        return containerList;
    }

    @Override
    public void prepare() throws Exception {
        String artifactsJsonString = resultsSummary.getCustomBuildData().get(ARTIFACT_BUILD_DATA_KEY);
        if (artifactsJsonString != null) {
            JSONArray artifacts = new JSONArray(artifactsJsonString);
            setNetReadMetrics(loadArtifact(ARTIFACT_PREFIX + "all-net-read"));
            setNetWriteMetrics(loadArtifact(ARTIFACT_PREFIX + "all-net-write"));
            for (Object artifactObject : artifacts) {
                JSONObject artifactJson = (JSONObject) artifactObject;
                String containerName = artifactJson.getString("name");
                int cpu = artifactJson.getInt("cpuRequest");
                int memory = artifactJson.getInt("memoryRequest");

                ContainerMetrics container = new ContainerMetrics(containerName, cpu, memory);
                container.setCpuMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-cpu"));
                container.setMemoryMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-memory"));
                container.setMemoryRssMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-memory-rss"));
                container.setMemoryCacheMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-memory-cache"));
                container.setMemorySwapMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-memory-swap"));
                container.setFsReadMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-fs-read"));
                container.setFsWriteMetrics(loadArtifact(ARTIFACT_PREFIX + containerName + "-fs-write"));
                containerList.add(container);
            }
        }

    }

    private String loadArtifact(String artifactName) {
        Artifact artifact = createArtifact(
                artifactName, resultsSummary.getPlanResultKey(),
                resultsSummary.getCustomBuildData().get(MetricsBuildProcessor.ARTIFACT_TYPE_BUILD_DATA_KEY));
        ArtifactLinkDataProvider artifactLinkDataProvider = artifactLinkManager.getArtifactLinkDataProvider(
                artifact);
        if (artifactLinkDataProvider == null) {
            addActionError("Unable to find artifact link data provider for artifact link");
            return null;
        }
        Iterable<ArtifactFileData> artifactFiles = artifactLinkDataProvider.listObjects("");
        ArtifactFileData single = getSingleDownloadableFile(artifactFiles);
        if (single != null) {
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
                return null;
            }

            return response.readEntity(String.class);
        }
        return null;
    }
    
    public String getNetWriteMetrics() {
        return netWriteMetrics;
    }

    public void setNetWriteMetrics(String netWriteMetrics) {
        this.netWriteMetrics = netWriteMetrics;
    }

    public String getNetReadMetrics() {
        return netReadMetrics;
    }

    public void setNetReadMetrics(String netReadMetrics) {
        this.netReadMetrics = netReadMetrics;
    }
    
}
