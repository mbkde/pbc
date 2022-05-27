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

package com.atlassian.buildeng.ecs.remote;

import com.atlassian.bamboo.Key;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.atlassian.buildeng.spi.isolated.docker.HostFolderMapping;
import com.atlassian.buildeng.spi.isolated.docker.HostFolderMappingModuleDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.utils.URIBuilder;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import static org.quartz.JobBuilder.newJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import org.quartz.Trigger;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);
    static String PLUGIN_JOB_KEY = "ecs-remote-watchdog";
    static long PLUGIN_JOB_INTERVAL_MILLIS = 60000L; //Reap once every 60 seconds
    
    //these 2 copied from bamboo-isolated-docker-plugin to avoid dependency
    static final String RESULT_PREFIX = "result.isolated.docker.";
    static final String RESULT_PART_TASKARN = "TaskARN";
    // The name of the agent container
    static final String AGENT_CONTAINER_NAME = "bamboo-agent";

    private final GlobalConfiguration globalConfiguration;
    private final Scheduler scheduler;
    private final PluginAccessor pluginAccessor;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration, 
            Scheduler scheduler, PluginAccessor pluginAccessor) {
        this.globalConfiguration = globalConfiguration;
        this.scheduler = scheduler;
        this.pluginAccessor = pluginAccessor;
    }



    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
        Client client = createClient();

        final WebResource resource = client.resource(globalConfiguration.getCurrentServer() + "/rest/scheduler");
//        resource.addFilter(new HTTPBasicAuthFilter(username, password));

        try {
            IsolatedDockerAgentResult result =
                    resource
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .post(IsolatedDockerAgentResult.class, createBody(request, globalConfiguration));
            logger.info("result:" + result.isRetryRecoverable() + " " + result.getErrors() + " " + result.getCustomResultData());
            callback.handle(result);
        }
        catch (UniformInterfaceException e) {
            int code = e.getResponse().getStatusInfo().getStatusCode();
            String s = "";
            if (e.getResponse().hasEntity()) {
                s = e.getResponse().getEntity(String.class);
            }
            logger.error("Error contacting ECS:" + code + " " + s, e);
            if (code == 504 || code == 503) { //gateway timeout/Service Unavailable
                callback.handle(new IsolatedDockerAgentResult().withRetryRecoverable(s));
            } else {
                callback.handle(new IsolatedDockerAgentException(e));
            }
        } catch (ClientHandlerException che) {
            logger.error("Error connecting to ECS:", che);
            callback.handle(new IsolatedDockerAgentResult().withRetryRecoverable(che.getMessage()));
        } catch (Exception t) {
            logger.error("unknown error", t);
            callback.handle(new IsolatedDockerAgentException(t));
        }
    }

    static Client createClient() {

        final ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        clientConfig.getProperties().put(
                ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true);
        clientConfig.getProperties().put(
                JSONConfiguration.FEATURE_POJO_MAPPING, true);
        Client client = Client.create(clientConfig);
        return client;
    }

    @Override
    public Map<String, URL> getContainerLogs(Configuration configuration, Map<String, String> customData) {
        String taskArn = customData.get(RESULT_PREFIX + RESULT_PART_TASKARN);
        if (taskArn == null) {
            return Collections.emptyMap();
        }
        Stream<String> s = Stream.concat(
                Stream.of(AGENT_CONTAINER_NAME),
                          configuration.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> t.getName()));
        return s.collect(Collectors.toMap(Function.identity(), (String t) -> {
            try {
                URIBuilder bb = new URIBuilder(globalConfiguration.getBambooBaseUrl() + "/rest/pbc-ecs-remote/latest/logs")
                        .addParameter(Rest.PARAM_CONTAINER, t)
                        .addParameter(Rest.PARAM_TASK_ARN, taskArn);
                return bb.build().toURL();
            } catch (URISyntaxException | MalformedURLException ex) {
                return null; //??
            }
        }));
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void reserveCapacity(Key buildKey, List<String> jobResultKeys, long excessMemoryCapacity, long excessCpuCapacity) {
        if (globalConfiguration.isPreemptiveScaling()) {
            Client client = createClient();
            final WebResource resource = client.resource(globalConfiguration.getCurrentServer() + "/rest/scheduler/future");
            try {
                resource
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .post(createFutureReqBody(buildKey, jobResultKeys, excessMemoryCapacity, excessCpuCapacity));
            }
            catch (UniformInterfaceException e) {
                int code = e.getResponse().getStatusInfo().getStatusCode();
                String s = "";
                if (e.getResponse().hasEntity()) {
                    s = e.getResponse().getEntity(String.class);
                }
                logger.error("Error contacting ECS wrt future:" + code + " " + s, e);
            } catch (ClientHandlerException che) {
                logger.error("Error connecting to ECS wrt future:", che);
            } catch (Exception t) {
                logger.error("unknown error", t);
            }
        }
    }

    @Override
    public void onStart() {
        JobDataMap config = new JobDataMap();
        config.put("globalConfiguration", globalConfiguration);
        config.put("isolatedAgentService", this);
        Trigger jobTrigger = newTrigger()
                .startNow()
                .withSchedule(simpleSchedule()
                        .withIntervalInMilliseconds(PLUGIN_JOB_INTERVAL_MILLIS)
                        .repeatForever()
                )
                .build();
        JobDetail pluginJob = newJob(RemoteWatchdogJob.class)
                .withIdentity(PLUGIN_JOB_KEY)
                .usingJobData(config)
                .build();
        try {
            scheduler.scheduleJob(pluginJob, jobTrigger);
        } catch (SchedulerException e) {
            logger.error("Unable to schedule RemoteWatchdogJob", e);
        }
    }

    @Override
    public void onStop() {
        try {
            boolean watchdogJobDeletion = scheduler.deleteJob(JobKey.jobKey(PLUGIN_JOB_KEY));
            if (!watchdogJobDeletion) {
                logger.warn("Was not able to delete ECS Remote Watchdog job. Was it already deleted?");
            }
        } catch (SchedulerException e) {
            logger.error("Remote ECS Backend Plugin is being stopped but is unable to unschedule RemoteWatchdogJob", e);
        }
    }

    private String createBody(IsolatedDockerAgentRequest request, GlobalConfiguration globalConfiguration) {
        JsonObject root = new JsonObject();
        root.addProperty("uuid", request.getUniqueIdentifier().toString());
        root.addProperty("queueTimestamp", request.getQueueTimestamp());
        root.addProperty("resultId", request.getResultKey());
        root.addProperty("bambooServer", globalConfiguration.getBambooBaseUrl());
        root.addProperty("sidekick", globalConfiguration.getCurrentSidekick());
        root.addProperty("taskARN", globalConfiguration.getCurrentRole());
        root.addProperty("buildKey", request.getBuildKey());
        root.add("hostFolderMappings", generateHostFolderMappings());
        root.add("configuration", ConfigurationPersistence.toJson(request.getConfiguration()));
        return root.toString();
    }

    private String createFutureReqBody(Key buildKey, List<String> jobResultKeys, long excessMemoryCapacity, long excessCpuCapacity) {
        JsonObject root = new JsonObject();
        root.addProperty("buildKey", buildKey.getKey());
        JsonArray arr = new JsonArray();
        jobResultKeys.forEach((String t) -> {
            arr.add(new JsonPrimitive(t));
        });
        root.add("resultKeys", arr);
        root.addProperty("cpu", excessCpuCapacity);
        root.addProperty("memory", excessMemoryCapacity);
        return root.toString();
    }

    private JsonArray generateHostFolderMappings() {
        JsonArray arr = new JsonArray();
        getHostFolderMappings().forEach((HostFolderMapping t) -> {
            JsonObject o = new JsonObject();
            o.addProperty("volumeName", t.getVolumeName());
            o.addProperty("hostPath", t.getHostPath());
            o.addProperty("containerPath", t.getContainerPath());
            arr.add(o);
        });
        return arr;
    }

    public List<HostFolderMapping> getHostFolderMappings() {
        return pluginAccessor.getEnabledModuleDescriptorsByClass(HostFolderMappingModuleDescriptor.class).stream()
                .map((HostFolderMappingModuleDescriptor t) -> t.getModule())
                .collect(Collectors.toList());
    }
}
