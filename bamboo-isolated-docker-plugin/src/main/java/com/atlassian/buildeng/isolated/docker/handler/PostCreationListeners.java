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

package com.atlassian.buildeng.isolated.docker.handler;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.deployments.projects.events.DeploymentProjectConfigUpdatedEvent;
import com.atlassian.bamboo.deployments.projects.events.DeploymentProjectCreatedEvent;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.event.BuildConfigurationUpdatedEvent;
import com.atlassian.bamboo.event.ChainCreatedEvent;
import com.atlassian.bamboo.exception.WebValidationException;
import com.atlassian.bamboo.persistence.HibernateRunner;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.TopLevelPlan;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.requirement.ImmutableRequirement;
import com.atlassian.bamboo.v2.events.BuildCreatedEvent;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.event.api.EventListener;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.acegisecurity.AccessDeniedException;
import org.slf4j.LoggerFactory;

/**
 * DockerHandler.appendConfiguration() cannot add requirements, neither do bamboo specs executions.
 * we listen on created/updated plans and deployments and update the requirement accordingly.
 */
public class PostCreationListeners {
    @SuppressWarnings("UnusedDeclaration")
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(PostCreationListeners.class);

    private final PlanManager pm;
    private final DeploymentProjectService deploymentProjectService;
    private final EnvironmentCustomConfigService environmentCustomConfigService;
    private final EnvironmentRequirementService environmentRequirementService;

    @Inject
    public PostCreationListeners(PlanManager pm,
            DeploymentProjectService deploymentProjectService,
            EnvironmentCustomConfigService environmentCustomConfigService,
            EnvironmentRequirementService environmentRequirementService) {
        this.pm = pm;
        this.deploymentProjectService = deploymentProjectService;
        this.environmentCustomConfigService = environmentCustomConfigService;
        this.environmentRequirementService = environmentRequirementService;
    }


    @EventListener
    public void onBuildCreatedEvent(BuildCreatedEvent event) {
        patchByPlanKey(event.getPlanKey());
    }

    private boolean patchJob(Job job) {
        if (job != null && !job.hasMaster()) {
            boolean isPresent = job
                    .getRequirementSet()
                    .getRequirements()
                    .stream()
                    .anyMatch((Requirement t) -> Constants.CAPABILITY_RESULT.equals(t.getKey()));
            Configuration c = AccessConfiguration.forJob(job);
            if (!isPresent && c.isEnabled()) {
                DockerHandlerImpl.addResultRequirement(job.getRequirementSet());
                return true;
            } else if (isPresent && !c.isEnabled()) {
                DockerHandlerImpl.removeAllRequirements(job.getRequirementSet());
                return true;
            }
        }
        return false;
    }

    private void patchByPlanKey(PlanKey key) {
        try {
            HibernateRunner.runWithHibernateSession(() -> {
                TopLevelPlan plan = pm.getPlanByKeyIfOfType(key, TopLevelPlan.class);
                if (plan != null && !plan.hasMaster()) {
                    boolean changedAny = plan
                            .getAllJobs()
                            .stream()
                            .map(this::patchJob)
                            .filter((Boolean t) -> Boolean.TRUE.equals(t))
                            .count() > 0;
                    if (changedAny) {
                        pm.savePlan(plan);
                    }
                } else {
                    Job job = pm.getPlanByKeyIfOfType(key, Job.class);
                    if (job != null && !job.hasMaster() && patchJob(job)) {
                        pm.savePlan(job);
                    }
                }
                return this;
            });
        } catch (Exception ex) {
            log.error("failed to update system requirement for pbc", ex);
        }
    }


    @EventListener
    public void onDeploymentProjectCreatedEvent(DeploymentProjectCreatedEvent event) {
        patchEnvironments(event.getDeploymentProjectId());
    }


    @EventListener
    public void onDeploymentProjectConfigUpdatedEvent(DeploymentProjectConfigUpdatedEvent event) {
        patchEnvironments(event.getDeploymentProjectId());
    }

    @EventListener
    public void onBuildConfigurationUpdatedEvent(BuildConfigurationUpdatedEvent event) {
        patchByPlanKey(event.getPlanKey());
    }

    @EventListener
    public void onChainCreatedEvent(ChainCreatedEvent event) {
        patchByPlanKey(event.getPlanKey());
    }

    private void patchEnvironments(long deploymentProjectId) throws AccessDeniedException {
        DeploymentProject dp = deploymentProjectService.getDeploymentProject(deploymentProjectId);
        if (dp == null || dp.getEnvironments() == null) {
            return;
        }
        dp.getEnvironments().forEach((Environment t) -> {
            try {
                List<? extends ImmutableRequirement> reqs =
                        environmentRequirementService.getRequirementsForEnvironment(t.getId());
                Configuration c = AccessConfiguration.forEnvironment(t, environmentCustomConfigService);
                boolean isPresent = reqs
                        .stream()
                        .anyMatch((ImmutableRequirement r) -> Constants.CAPABILITY_RESULT.equals(r.getKey()));
                if (!isPresent && c.isEnabled()) {
                    DockerHandlerImpl.addEnvironementRequirement(t, environmentRequirementService);
                } else if (isPresent && !c.isEnabled()) {
                    DockerHandlerImpl.removeEnvironmentRequirements(t, environmentRequirementService);
                }
            } catch (WebValidationException ex) {
                Logger.getLogger(PostCreationListeners.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

}
