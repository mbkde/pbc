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

package com.atlassian.buildeng.ecs;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import com.amazonaws.services.ecs.model.ClientException;
import com.atlassian.bamboo.Key;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.logs.AwsLogs;
import com.atlassian.buildeng.ecs.scheduling.DefaultSchedulingCallback;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.ReserveRequest;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.SchedulingRequest;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.utils.URIBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {

    private static final Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);

    private final GlobalConfiguration globalConfiguration;
    private final ECSScheduler ecsScheduler;
    private final Scheduler scheduler;
    private final SchedulerBackend schedulerBackend;
    private final TaskDefinitionRegistrations taskDefRegistrations;
    // not used in the class but in the bundled library and apparently in that case for
    // REASONS the class is not found and used at injection time.
    // so I presume bytecode of bundled libs is not scanned while the sources of the plugin are in some way.
    // and some spring related metadata is created for them.
    private final EventPublisher eventPublisher;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration,
            ECSScheduler ecsScheduler,
            Scheduler scheduler,
            SchedulerBackend schedulerBackend,
            TaskDefinitionRegistrations taskDefRegistrations,
            EventPublisher eventPublisher) {
        this.globalConfiguration = globalConfiguration;
        this.ecsScheduler = ecsScheduler;
        this.scheduler = scheduler;
        this.schedulerBackend = schedulerBackend;
        this.taskDefRegistrations = taskDefRegistrations;
        this.eventPublisher = eventPublisher;
    }

    // Isolated Agent Service methods
    @Override
    public void startAgent(IsolatedDockerAgentRequest req, IsolatedDockerRequestCallback callback) {
        int revision = taskDefRegistrations.findTaskRegistrationVersion(req.getConfiguration(), globalConfiguration);
        String resultId = req.getResultKey();
        if (revision == -1) {
            try {
                revision = taskDefRegistrations.registerDockerImage(req.getConfiguration(), globalConfiguration);
            } catch (ECSException ex) {
                logger.info("Failed to receive task definition for {} and {}",
                        globalConfiguration.getTaskDefinitionName(),
                        resultId);
                // Have to catch some of the exceptions here instead of the callback to use retries.
                if (ex.getCause() instanceof ClientException &&
                        ex
                                .getMessage()
                                .contains(
                                        "Too many concurrent attempts to create a new revision of the specified family")) {
                    IsolatedDockerAgentResult toRet = new IsolatedDockerAgentResult();
                    toRet.withRetryRecoverable("Hit Api limit for task revisions.");
                    callback.handle(toRet);
                } else {
                    callback.handle(ex);
                }
                return;
            }
        }
        logger.info("Spinning up new docker agent from task definition {}:{} {}",
                globalConfiguration.getTaskDefinitionName(),
                revision,
                resultId);
        ContainerSizeDescriptor sizeDescriptor = globalConfiguration.getSizeDescriptor();
        SchedulingRequest schedulingRequest = new SchedulingRequest(req.getUniqueIdentifier(),
                resultId,
                revision,
                req.getConfiguration().getCPUTotal(sizeDescriptor),
                req.getConfiguration().getMemoryTotal(sizeDescriptor),
                req.getConfiguration(),
                req.getQueueTimestamp(),
                req.getBuildKey());
        ecsScheduler.schedule(schedulingRequest, new DefaultSchedulingCallback(callback, resultId));
    }

    @Override
    public List<String> getKnownDockerImages() {
        List<String> toRet = globalConfiguration
                .getAllRegistrations()
                .keySet()
                .stream()
                .flatMap((Configuration t) -> getAllImages(t))
                .distinct()
                .collect(Collectors.toList());
        // sort for sake of UI/consistency?
        Collections.sort(toRet);
        return toRet;
    }

    @Override
    public Map<String, URL> getContainerLogs(Configuration configuration, Map<String, String> customData) {
        String taskArn = customData.get(Constants.RESULT_PREFIX + Constants.RESULT_PART_TASKARN);
        if (taskArn == null || AwsLogs.getAwsLogsDriver(globalConfiguration) == null) {
            return Collections.emptyMap();
        }
        Stream<String> s = Stream.concat(Stream.of(Constants.AGENT_CONTAINER_NAME),
                configuration.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> t.getName()));
        return s.collect(Collectors.toMap(Function.identity(), (String t) -> {
            try {
                URIBuilder bb = new URIBuilder(globalConfiguration.getBambooBaseUrl() + "/rest/docker/latest/logs")
                        .addParameter(Rest.PARAM_CONTAINER, t)
                        .addParameter(Rest.PARAM_TASK_ARN, taskArn);
                return bb.build().toURL();
            } catch (URISyntaxException | MalformedURLException ex) {
                return null; //??
            }
        }));
    }


    Stream<String> getAllImages(Configuration c) {
        return Stream.concat(Stream.of(c.getDockerImage()), c.getExtraContainers().stream().map(ec -> ec.getImage()));
    }

    @Override
    public void reserveCapacity(Key buildKey, List<String> jobResultKeys, long memoryCapacity, long cpuCapacity) {
        if (globalConfiguration.isPreemptiveScaling()) {
            logger.info("Reserving future capacity for {}: mem:{} cpu:{}", buildKey, memoryCapacity, cpuCapacity);
            ecsScheduler.reserveFutureCapacity(new ReserveRequest(buildKey.getKey(),
                    jobResultKeys,
                    cpuCapacity,
                    memoryCapacity));
        } else {
            logger.info("Reserving future capacity is disabled");
        }
    }

    @Override
    public void onStart() {
        JobDataMap config = new JobDataMap();
        config.put("globalConfiguration", globalConfiguration);
        config.put("schedulerBackend", schedulerBackend);
        config.put("isolatedAgentService", this);
        Trigger jobTrigger = newTrigger()
                .startNow()
                .withSchedule(simpleSchedule()
                        .withIntervalInMilliseconds(Constants.PLUGIN_JOB_INTERVAL_MILLIS)
                        .repeatForever())
                .build();
        JobDetail pluginJob =
                newJob(ECSWatchdogJob.class).withIdentity(Constants.PLUGIN_JOB_KEY).usingJobData(config).build();
        try {
            scheduler.scheduleJob(pluginJob, jobTrigger);
        } catch (SchedulerException e) {
            logger.error("Unable to schedule RemoteWatchdogJob", e);
        }
    }

    @Override
    public void onStop() {
        try {
            boolean watchdogJobDeletion = scheduler.deleteJob(JobKey.jobKey(Constants.PLUGIN_JOB_KEY));
            if (!watchdogJobDeletion) {
                logger.warn("Was not able to delete ECS Watchdog job. Was it already deleted?");
            }
        } catch (SchedulerException e) {
            logger.error("Remote ECS Backend Plugin is being stopped but is unable to delete RemoteWatchdogJob", e);
        }
    }
}
