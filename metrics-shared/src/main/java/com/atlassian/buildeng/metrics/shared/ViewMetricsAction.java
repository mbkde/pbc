/*
 * Copyright 2016 Atlassian.
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

package com.atlassian.buildeng.metrics.shared;

import com.atlassian.bamboo.archive.ArchiverType;
import com.atlassian.bamboo.artifact.Artifact;
import com.atlassian.bamboo.build.PlanResultsAction;
import com.atlassian.bamboo.build.artifact.ArtifactFileData;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.util.BambooIterables;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.collect.Iterables;
import com.opensymphony.xwork2.Preparable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ViewMetricsAction extends PlanResultsAction implements Preparable {

    Logger log = LoggerFactory.getLogger(ViewMetricsAction.class);

    private Configuration configuration;

    public Configuration getConfiguration() {
        return configuration;
    }

    @Nullable
    protected ArtifactFileData getSingleDownloadableFile(@NotNull final Iterable<ArtifactFileData> urls) {
        final boolean isSingleFile = BambooIterables.hasSize(urls, 1);
        if (!isSingleFile) {
            return null;
        }
        final ArtifactFileData singleFile = Iterables.getOnlyElement(urls);
        final boolean isSingleDownloadableFile = singleFile.getFileType() == ArtifactFileData.FileType.REGULAR_FILE;
        return isSingleDownloadableFile ? singleFile : null;
    }

    protected Artifact createArtifact(String label, PlanResultKey prk, String linkType) {
        return new Artifact() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public long getSize() {
                return 1;
            }

            @Override
            public String getLinkType() {
                return linkType;
            }

            @Override
            public boolean isSharedArtifact() {
                return false;
            }

            @Override
            public boolean isGloballyStored() {
                return false;
            }

            @Override
            public PlanResultKey getPlanResultKey() {
                return prk;
            }

            @Override
            public ArchiverType getArchiverType() {
                return ArchiverType.NONE;
            }

            @Override
            public long getId() {
                return 0;
            }
        };
    }
}
