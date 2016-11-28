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

package com.atlassian.buildeng.ecs.remote;

import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {
    private final static Logger logger = LoggerFactory.getLogger(ECSIsolatedAgentServiceImpl.class);
    static String PLUGIN_JOB_KEY = "ecs-remote-watchdog";
    static long PLUGIN_JOB_INTERVAL_MILLIS = 60000L; //Reap once every 60 seconds

    private final GlobalConfiguration globalConfiguration;
    private final PluginScheduler pluginScheduler;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration, PluginScheduler pluginScheduler) {
        this.globalConfiguration = globalConfiguration;
        this.pluginScheduler = pluginScheduler;
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
            int code = e.getResponse().getClientResponseStatus().getStatusCode();
            String s = "";
            if (e.getResponse().hasEntity()) {
                s = e.getResponse().getEntity(String.class);
            }
            logger.info("error:" + code + " " + s, e);
            callback.handle(new IsolatedDockerAgentException(e));
        } catch (Throwable t) {
            logger.info("error", t);
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
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        config.put("globalConfiguration", globalConfiguration);
        pluginScheduler.scheduleJob(PLUGIN_JOB_KEY, RemoteWatchdogJob.class, config, new Date(), PLUGIN_JOB_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(PLUGIN_JOB_KEY);
    }

    private String createBody(IsolatedDockerAgentRequest request, GlobalConfiguration globalConfiguration) {
        JsonObject root = new JsonObject();
        root.addProperty("uuid", request.getUniqueIdentifier().toString());
        root.addProperty("resultId", request.getResultKey());
        root.addProperty("bambooServer", globalConfiguration.getBambooBaseUrl());
        root.addProperty("sidekick", globalConfiguration.getCurrentSidekick());
        root.addProperty("taskARN", globalConfiguration.getCurrentRole());
        root.add("configuration", ConfigurationPersistence.toJson(request.getConfiguration()));
        return root.toString();
    }

}
