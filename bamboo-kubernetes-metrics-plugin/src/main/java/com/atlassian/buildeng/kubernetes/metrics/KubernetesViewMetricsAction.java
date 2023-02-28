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
import com.atlassian.bamboo.build.artifact.FileSystemArtifactLinkDataProvider;
import com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor;
import com.atlassian.buildeng.metrics.shared.ViewMetricsAction;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class KubernetesViewMetricsAction extends ViewMetricsAction {

    public final class ContainerMetrics {
        private final String containerName;
        private String cpuMetrics;
        private String cpuUserMetrics;
        private String cpuSystemMetrics;
        private String memoryMetrics;
        private String memoryRssMetrics;
        private String memoryCacheMetrics;
        private String memorySwapMetrics;
        private String fsWriteMetrics;
        private String fsReadMetrics;
        private final int memoryLimit;
        private final int cpuLimit;
        private final int memoryRequest;

        ContainerMetrics(String containerName, int cpuLimit, int memoryLimit, int memoryRequest) {
            this.containerName = containerName;
            this.cpuLimit = cpuLimit;
            this.memoryLimit = memoryLimit;
            this.memoryRequest = memoryRequest;
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

        public String getCpuUserMetrics() {
            return cpuUserMetrics;
        }

        public void setCpuUserMetrics(String cpuUserMetrics) {
            this.cpuUserMetrics = cpuUserMetrics;
        }

        public String getCpuSystemMetrics() {
            return cpuSystemMetrics;
        }

        public void setCpuSystemMetrics(String cpuSystemMetrics) {
            this.cpuSystemMetrics = cpuSystemMetrics;
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

        public int getMemoryRequest() {
            return memoryRequest;
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
            setNetReadMetrics(loadArtifact("", "net-read"));
            setNetWriteMetrics(loadArtifact("", "net-write"));
            for (Object artifactObject : artifacts) {
                JSONObject artifactJson = (JSONObject) artifactObject;
                String containerName = artifactJson.getString("name");
                int cpu = artifactJson.getInt("cpuRequest");
                int memory = artifactJson.getInt("memoryRequest");
                int memoryLimit = artifactJson.optInt(
                        "memoryLimit", (int) (memory * DefaultContainerSizeDescriptor.SOFT_TO_HARD_LIMIT_RATIO));

                ContainerMetrics cont = new ContainerMetrics(containerName, cpu, memoryLimit, memory);
                cont.setCpuMetrics(loadArtifact(containerName, "-cpu"));
                cont.setCpuUserMetrics(loadArtifact(containerName, "-cpu-user"));
                cont.setCpuSystemMetrics(loadArtifact(containerName, "-cpu-system"));
                cont.setMemoryMetrics(loadArtifact(containerName, "-memory"));
                cont.setMemoryRssMetrics(loadArtifact(containerName, "-memory-rss"));
                cont.setMemoryCacheMetrics(loadArtifact(containerName, "-memory-cache"));
                cont.setMemorySwapMetrics(loadArtifact(containerName, "-memory-swap"));
                cont.setFsReadMetrics(loadArtifact(containerName, "-fs-read"));
                cont.setFsWriteMetrics(loadArtifact(containerName, "-fs-write"));
                containerList.add(cont);
            }
        }
    }

    private String loadArtifact(String containerName, String suffix) {
        String artifactName = ARTIFACT_PREFIX + containerName + suffix;

        Artifact artifact = createArtifact(
                artifactName,
                resultsSummary.getPlanResultKey(),
                resultsSummary.getCustomBuildData().get(MetricsBuildProcessor.ARTIFACT_TYPE_BUILD_DATA_KEY));

        ArtifactLinkDataProvider artifactLinkDataProvider;

        try {
            artifactLinkDataProvider = artifactLinkManager.getArtifactLinkDataProvider(artifact);
        } catch (IllegalArgumentException e) {
            addActionError("Failed to get artifactLinkManager: " + e.getMessage());
            return null;
        }

        if (artifactLinkDataProvider instanceof FileSystemArtifactLinkDataProvider) {
            return loadArtifactFile(((FileSystemArtifactLinkDataProvider) artifactLinkDataProvider).getFile());
        }

        if (artifactLinkDataProvider == null) {
            addActionError("Unable to find artifact link data provider for artifact link");
            return null;
        }
        Iterable<ArtifactFileData> artifactFiles = artifactLinkDataProvider.listObjects("");
        ArtifactFileData single = getSingleDownloadableFile(artifactFiles);
        if (single != null) {
            Client client = Client.create();
            String singleUrl = single.getUrl();
            URI singleURI = URI.create(singleUrl);
            if (!singleURI.isAbsolute()) {
                singleURI = URI.create(getBaseUrl() + (singleUrl.startsWith("/") ? "" : "/") + singleUrl);
            }
            WebResource webTarget = client.resource(singleURI);

            // We have to directly retrieve the artifact here instead of passing the URL to the user due to
            // same-origin policy.
            try {
                return webTarget.accept(MediaType.APPLICATION_JSON).get(String.class);
            } catch (UniformInterfaceException e) {
                addActionError(String.format("Error retrieving metrics JSON artifact from %s", single.getUrl()));
                return null;
            }
        }
        return null;
    }

    private String loadArtifactFile(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            addActionError("Unable to load artifact file " + file);
            return null;
        }
        try {
            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            addActionError("Unable to load artifact file " + file);
            return null;
        }
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
