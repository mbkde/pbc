/*
 * Copyright 2022 Atlassian Pty Ltd.
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
import com.atlassian.bamboo.build.artifact.ArtifactLinkManager;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.chains.BuildContextFactory;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.jsonator.Jsonator;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.plan.PlanExecutionManager;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bamboo.utils.i18n.DocumentationLinkProvider;
import com.atlassian.bamboo.vcs.configuration.service.VcsRepositoryConfigurationService;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.BambooImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.web.WebInterfaceManager;

public class ComponentImports {
    @ComponentImport
    public BandanaManager bandanaManager;

    @BambooImport
    public AuditLogService auditLogService;

    @BambooImport
    public AdministrationConfigurationAccessor administrationConfigurationAccessor;

    @BambooImport
    public BambooAuthenticationContext authenticationContext;

    @ComponentImport
    public BuildLoggerManager buildLoggerManager;

    @ComponentImport
    public ArtifactManager artifactManager;

    @BambooImport
    public ResultsSummaryManager resultsSummaryManager;

    @BambooImport
    public ArtifactLinkManager artifactLinkManager;

    @BambooImport
    public VcsRepositoryConfigurationService vcsRepositoryConfigurationService;

    @BambooImport
    public BambooPermissionManager bambooPermissionManager;

    @BambooImport
    public WebInterfaceManager webInterfaceManager;

    @BambooImport
    public PlanExecutionManager planExecutionManager;

    @BambooImport
    public Jsonator jsonator;

    @BambooImport
    public BuildContextFactory buildContextFactory;

    @BambooImport
    public DocumentationLinkProvider documentationLinkProvider;
}
