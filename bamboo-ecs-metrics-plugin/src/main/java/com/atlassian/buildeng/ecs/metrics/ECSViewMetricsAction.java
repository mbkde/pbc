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

package com.atlassian.buildeng.ecs.metrics;

import com.atlassian.bamboo.artifact.Artifact;
import com.atlassian.bamboo.build.artifact.ArtifactFileData;
import com.atlassian.bamboo.build.artifact.ArtifactLinkDataProvider;
import com.atlassian.buildeng.metrics.shared.MetricsBuildProcessor;
import com.atlassian.buildeng.metrics.shared.ViewMetricsAction;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.List;


public class ECSViewMetricsAction extends ViewMetricsAction {
    static final String ARTIFACT_BUILD_DATA_KEY = "ecs_metrics_artifacts";

    private List<String> urls = new ArrayList<>();

    public List<String> getBambooAgentUrls() {
        return urls;
    }

    @Override
    public void prepare() throws Exception {
        String artifactNames = resultsSummary.getCustomBuildData().get(ARTIFACT_BUILD_DATA_KEY);
        if (artifactNames != null) {
            Splitter.on(",").splitToList(artifactNames).forEach((String t) -> {
                Artifact artifact = createArtifact(
                        t, resultsSummary.getPlanResultKey(),
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
                    urls.add(single.getUrl());
                }
            });
        }
    }
}
