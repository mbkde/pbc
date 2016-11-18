/*
 * Copyright 2015 Atlassian.
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

package com.atlassian.buildeng.ecs;

import com.amazonaws.services.ecs.model.ClientException;
import com.atlassian.buildeng.ecs.scheduling.DefaultSchedulingCallback;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.SchedulingRequest;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);

    private final GlobalConfiguration globalConfiguration;
    private final ECSScheduler ecsScheduler;
    private final PluginScheduler pluginScheduler;
    private final SchedulerBackend schedulerBackend;
    private final TaskDefinitionRegistrations taskDefRegistrations;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration, ECSScheduler ecsScheduler, 
            PluginScheduler pluginScheduler, SchedulerBackend schedulerBackend, TaskDefinitionRegistrations taskDefRegistrations) {
        this.globalConfiguration = globalConfiguration;
        this.ecsScheduler = ecsScheduler;
        this.pluginScheduler = pluginScheduler;
        this.schedulerBackend = schedulerBackend;
        this.taskDefRegistrations = taskDefRegistrations;
    }

    // Isolated Agent Service methods
    @Override
    public void startAgent(IsolatedDockerAgentRequest req, IsolatedDockerRequestCallback callback) {
        int revision = taskDefRegistrations.findTaskRegistrationVersion(req.getConfiguration(), globalConfiguration);
        String resultId = req.getResultKey();
        if (revision == -1) {
            try {
                revision = taskDefRegistrations.registerDockerImage(req.getConfiguration(), globalConfiguration);
            } catch (ImageAlreadyRegisteredException | ECSException ex) {
                logger.info("Failed to receive task definition for {} and {}", globalConfiguration.getTaskDefinitionName(), resultId);
                //Have to catch some of the exceptions here instead of the callback to use retries.
                if(ex.getCause() instanceof ClientException && ex.getMessage().contains("Too many concurrent attempts to create a new revision of the specified family")) {
                    IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
                    toRet.withRetryRecoverable("Hit Api limit for task revisions.");
                    callback.handle(toRet);
                } else {
                    callback.handle(ex);
                }
                return;
            }
        }
        logger.info("Spinning up new docker agent from task definition {}:{} {}", globalConfiguration.getTaskDefinitionName(), revision, resultId);
        SchedulingRequest schedulingRequest = new SchedulingRequest(
                req.getUniqueIdentifier(),
                resultId,
                revision,
                req.getConfiguration());
        ecsScheduler.schedule(schedulingRequest, new DefaultSchedulingCallback(callback, resultId));
    }
    
    @Override
    public List<String> getKnownDockerImages() {
        List<String> toRet = globalConfiguration.getAllRegistrations().keySet().stream()
                .flatMap((Configuration t) -> getAllImages(t)) 
                .distinct()
                .collect(Collectors.toList());
        // sort for sake of UI/consistency?
        Collections.sort(toRet);
        return toRet;
    }
    
    Stream<String> getAllImages(Configuration c) {
        return Stream.concat(
                Stream.of(c.getDockerImage()), 
                c.getExtraContainers().stream().map(ec -> ec.getImage()));
    }


    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        config.put("globalConfiguration", globalConfiguration);
        config.put("schedulerBackend", schedulerBackend);
        pluginScheduler.scheduleJob(Constants.PLUGIN_JOB_KEY, ECSWatchdogJob.class, config, new Date(), Constants.PLUGIN_JOB_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(Constants.PLUGIN_JOB_KEY);
    }


}
