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

import com.atlassian.event.api.EventPublisher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatadogEventPublisher implements EventPublisher{
    static final String DATADOG_API = "DATADOG_API_KEY";
    private static final Logger logger = LoggerFactory.getLogger(DatadogEventPublisher.class);
    CloseableHttpClient httpclient = HttpClients.createDefault();
    private final String token;

    @Inject
    public DatadogEventPublisher(@Named(DATADOG_API) String apiToken) {
        this.token = apiToken;
    }

    @Override
    public void publish(Object event) {
        HttpPost httpPost = new HttpPost("https://app.datadoghq.com/api/v1/events?api_key=" + token);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        httpPost.setEntity(new StringEntity(createDDEvent(event), "UTF-8"));
        try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
            logger.info("Sent Datadog event, response: {}, api_key:{}", response.getStatusLine().getStatusCode(), token.substring(0, 3) + "???" + token.substring(token.length() - 3));
        } catch (IOException ex) {
            logger.error("Error while sending datadog event", ex);
        }
    }

    @Override
    public void register(Object listener) {
    }

    @Override
    public void unregister(Object listener) {
    }

    @Override
    public void unregisterAll() {
    }

    private String createDDEvent(Object event) {
        // a copy of what is in the monitoring + datadog plugin
        JsonObject body = new JsonObject();
        body.addProperty("title", "bamboo.server." + event.getClass().getSimpleName());
        body.addProperty("text", event.toString());
        body.addProperty("priority", "normal");
        body.addProperty("alert_type", "info");
        JsonArray tags = new JsonArray();
        tags.add(new JsonPrimitive("pbc"));
        tags.add(new JsonPrimitive("service_type:pbc_scheduler"));
        tags.add(new JsonPrimitive("service_name:pbc_scheduler"));
        body.add("tags", tags);
        return body.toString();
    }
}
