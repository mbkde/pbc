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
import com.atlassian.bamboo.persistence.HibernateRunner;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.events.BuildCreatedEvent;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.lifecycle.BuildProcessorServerImpl;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.event.api.EventListener;
import org.slf4j.LoggerFactory;

/**
 * DockerHandler.appendConfiguration() cannot add requirements, that's the sole purpose of this class.
 */ 
public class BuildCreatedEventListener {
    @SuppressWarnings("UnusedDeclaration")
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(BuildCreatedEventListener.class);
    
    private final PlanManager pm;

    public BuildCreatedEventListener(PlanManager pm) {
        this.pm = pm;
    }
    
    
    @EventListener
    public void onBuildCreatedEvent(BuildCreatedEvent event) {
        try {
            HibernateRunner.runWithHibernateSession(() -> {
                Job job = pm.getPlanByKey(event.getPlanKey(), Job.class);
                if (job != null && job.getMaster() == null) {
                    boolean isPresent = job.getRequirementSet().getRequirements().stream()
                            .filter((Requirement t) -> Constants.CAPABILITY_RESULT.equals(t.getKey()))
                            .findFirst().isPresent();
                    if (!isPresent && AccessConfiguration.forJob(job).isEnabled()) {
                        BuildProcessorServerImpl.addResultRequirement(job.getRequirementSet());
                    }
                    pm.savePlan(job);
                }
                return this;
            });
        } catch (Exception ex) {
            log.error("failed to update system requirement for pbc", ex);
        }
    }
}
