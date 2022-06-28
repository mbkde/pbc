/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.rest;

public class Config {

    public String sidekickImage;
    public String podTemplate;
    public String architecturePodConfig;
    public String iamRequestTemplate;
    public String iamSubjectIdPrefix;
    public String containerSizes;
    public String podLogsUrl;
    public String currentContext;
    public boolean useClusterRegistry;
    public String clusterRegistryAvailableSelector;
    public String clusterRegistryPrimarySelector;
    public String artifactoryCacheAllowList;
    public String artifactoryCachePodSpec;


    public Config() {
    }

    public Config(String sidekickImage, String currentContext, String podTemplate, String architecturePodConfig,
                  String iamRequestTemplate, String iamSubjectIdPrefix, String podLogsUrl, String containerSizes,
                  boolean useClusterRegistry, String clusterRegistryAvailableSelector,
                  String clusterRegistryPrimarySelector, String artifactoryCacheAllowList, String artifactoryCachePodSpec) {
        this.sidekickImage = sidekickImage;
        this.currentContext = currentContext;
        this.podTemplate = podTemplate;
        this.architecturePodConfig = architecturePodConfig;
        this.iamRequestTemplate = iamRequestTemplate;
        this.iamSubjectIdPrefix = iamSubjectIdPrefix;
        this.podLogsUrl = podLogsUrl;
        this.containerSizes = containerSizes;
        this.useClusterRegistry = useClusterRegistry;
        this.clusterRegistryPrimarySelector = clusterRegistryPrimarySelector;
        this.clusterRegistryAvailableSelector = clusterRegistryAvailableSelector;
        this.artifactoryCacheAllowList = artifactoryCacheAllowList;
        this.artifactoryCachePodSpec = artifactoryCachePodSpec;
    }

    public String getSidekickImage() {
        return sidekickImage;
    }

    public void setSidekickImage(String sidekickImage) {
        this.sidekickImage = sidekickImage;
    }

    public String getCurrentContext() {
        return currentContext;
    }

    public void setCurrentContext(String currentContext) {
        this.currentContext = currentContext;
    }

    public String getPodTemplate() {
        return podTemplate;
    }

    public void setPodTemplate(String podTemplate) {
        this.podTemplate = podTemplate;
    }

    public String getArchitecturePodConfig() { return architecturePodConfig; }

    public void setArchitecturePodConfig(String architecturePodConfig) { this.architecturePodConfig = architecturePodConfig; }

    public void setIamRequestTemplate(String iamRequestTemplate) {
        this.iamRequestTemplate = iamRequestTemplate;
    }

    public String getIamRequestTemplate() {
        return iamRequestTemplate;
    }

    public void setIamSubjectIdPrefix(String iamSubjectIdPrefix) {
        this.iamSubjectIdPrefix = iamSubjectIdPrefix;
    }

    public String getIamSubjectIdPrefix() {
        return iamSubjectIdPrefix;
    }

    public String getPodLogsUrl() {
        return podLogsUrl;
    }

    public void setPodLogsUrl(String podLogsUrl) {
        this.podLogsUrl = podLogsUrl;
    }

    public String getContainerSizes() {
        return containerSizes;
    }

    public void setContainerSizes(String containerSizes) {
        this.containerSizes = containerSizes;
    }

    public boolean isUseClusterRegistry() {
        return useClusterRegistry;
    }

    public void setUseClusterRegistry(boolean useClusterRegistry) {
        this.useClusterRegistry = useClusterRegistry;
    }

    public String getClusterRegistryAvailableSelector() {
        return clusterRegistryAvailableSelector;
    }

    public void setClusterRegistryAvailableSelector(String clusterRegistryAvailableSelector) {
        this.clusterRegistryAvailableSelector = clusterRegistryAvailableSelector;
    }

    public String getClusterRegistryPrimarySelector() {
        return clusterRegistryPrimarySelector;
    }

    public void setClusterRegistryPrimarySelector(String clusterRegistryPrimarySelector) {
        this.clusterRegistryPrimarySelector = clusterRegistryPrimarySelector;
    }

    public String getArtifactoryCacheAllowList() {
        return artifactoryCacheAllowList;
    }

    public void setArtifactoryCacheAllowList(String artifactoryCacheAllowList) {
        this.artifactoryCacheAllowList = artifactoryCacheAllowList;
    }

    public String getArtifactoryCachePodSpec() {
        return artifactoryCachePodSpec;
    }

    public void setArtifactoryCachePodSpec(String artifactoryCachePodSpec) {
        this.artifactoryCachePodSpec = artifactoryCachePodSpec;
    }
}
