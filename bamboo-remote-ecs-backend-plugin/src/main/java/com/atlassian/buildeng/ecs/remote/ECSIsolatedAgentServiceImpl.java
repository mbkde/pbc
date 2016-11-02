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
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.MediaType;

public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {

    private final GlobalConfiguration globalConfiguration;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }



    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
        Client client = createClient();

        final WebResource resource = client.resource(globalConfiguration.getCurrentServer() + "/rest/scheduler");
//        resource.addFilter(new HTTPBasicAuthFilter(username, password));

        try
        {
            IsolatedDockerAgentResult result =
                    resource
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .post(IsolatedDockerAgentResult.class, createBody(request, globalConfiguration));
            callback.handle(result);
        }
        catch (UniformInterfaceException e)
        {
            int code = e.getResponse().getClientResponseStatus().getStatusCode();
            if (e.getResponse().hasEntity())
            {
                String s = e.getResponse().getEntity(String.class);
            }
            callback.handle(new IsolatedDockerAgentException(e));
        }
    }

    static Client createClient() {
        final ClientConfig clientConfig = new DefaultClientConfig();
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
    }

    @Override
    public void onStop() {
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
