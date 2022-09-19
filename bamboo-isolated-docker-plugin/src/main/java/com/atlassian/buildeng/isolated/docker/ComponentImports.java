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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.FeatureManager;
import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.buildqueue.manager.AgentAssignmentService;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.capability.AgentContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.spring.scanner.annotation.imports.BambooImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.struts.TextProvider;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Qualifier;

public class ComponentImports {
    @ComponentImport
    public AgentContext agentContext;

    @ComponentImport
    public BuildLoggerManager buildLoggerManager;

    @BambooImport
    public IsolatedAgentService isolatedAgentService;

    @BambooImport
    public ContainerSizeDescriptor containerSizeDescriptor;

    @BambooImport
    public Scheduler scheduler;

    @BambooImport
    public FeatureManager featureManager;

    @BambooImport
    public AuditLogService auditLogService;

    // Available on agent but we don't need it
    @BambooImport
    public BandanaManager bandanaManager;

    @BambooImport
    public BambooAuthenticationContext authenticationContext;

    @BambooImport
    public EventPublisher eventPublisher;

    @BambooImport
    public BuildQueueManager buildQueueManager;

    @BambooImport
    public AgentManager agentManager;

    @BambooImport
    public AgentCommandSender agentCommandSender;

    @BambooImport
    public CachedPlanManager cachedPlanManager;

    // Available on agent but we don't need it
    @BambooImport
    public ErrorUpdateHandler errorUpdateHandler;

    @BambooImport
    public AgentAssignmentService agentAssignmentService;

    @BambooImport
    public ExecutableAgentsHelper executableAgentsHelper;

    @BambooImport
    public TextProvider textProvider;

    @BambooImport
    public ModuleDescriptor moduleDescriptor;

    @BambooImport
    public TemplateRenderer templateRenderer;

    @BambooImport
    public EnvironmentCustomConfigService environmentCustomConfigService;

    @BambooImport
    public WebResourceManager webResourceManager;

    @BambooImport
    public EnvironmentRequirementService environmentRequirementService;

    @BambooImport
    public DeploymentResultService deploymentResultService;

    @BambooImport
    public DeploymentExecutionService deploymentExecutionService;

    @BambooImport
    public PlanManager pm;

    @BambooImport
    public DeploymentProjectService deploymentProjectService;

    @BambooImport
    public BuildExecutionManager buildExecutionManager;

    @BambooImport
    public ResultsSummaryManager resultsSummaryManager;

}
