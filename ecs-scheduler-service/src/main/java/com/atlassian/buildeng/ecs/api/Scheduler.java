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
package com.atlassian.buildeng.ecs.api;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.atlassian.buildeng.spi.isolated.docker.HostFolderMapping;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

public class Scheduler {

    //silly BUT we already (de)serialize Configuration this way
    public static Scheduler fromJson(String v) {
        JsonParser p = new JsonParser();
        JsonElement obj = p.parse(v);
        if (obj.isJsonObject()) {
            JsonObject oo = obj.getAsJsonObject();
            JsonPrimitive uuid = oo.getAsJsonPrimitive("uuid");
            JsonPrimitive resultId = oo.getAsJsonPrimitive("resultId");
            JsonPrimitive server = oo.getAsJsonPrimitive("bambooServer");
            JsonPrimitive sidekick = oo.getAsJsonPrimitive("sidekick");
            JsonObject conf = oo.getAsJsonObject("configuration");
            if (uuid == null || resultId == null || conf == null || server == null || sidekick == null) {
                throw new IllegalArgumentException("Wrong format!");
            }
            Configuration c = ConfigurationPersistence.toConfiguration(conf.toString());
            if (c == null) {
                throw new IllegalArgumentException("Wrong format!");
            }
            final List<HostFolderMapping> mappings = new ArrayList<>();
            JsonArray hostMappings = oo.getAsJsonArray("hostFolderMappings");
            if (hostMappings != null) {
                hostMappings.forEach((JsonElement t) -> {
                    JsonObject o = (JsonObject) t;
                    JsonPrimitive name = o.getAsJsonPrimitive("volumeName");
                    JsonPrimitive hostPath = o.getAsJsonPrimitive("hostPath");
                    JsonPrimitive containerPath = o.getAsJsonPrimitive("containerPath");
                    if (name != null && hostPath != null && containerPath != null) {
                        mappings.add(new HostFolderMappingImpl(name.getAsString(), hostPath.getAsString(), containerPath.getAsString()));
                    }
                });
            }
            Scheduler scheduler = new Scheduler(uuid.getAsString(), resultId.getAsString(), server.getAsString(), sidekick.getAsString(), c, mappings);

            JsonPrimitive taskArn = oo.getAsJsonPrimitive("taskARN");
            if (taskArn != null) {
                scheduler.setTaskARN(taskArn.getAsString());
            }
            JsonPrimitive queueTimestamp = oo.getAsJsonPrimitive("queueTimestamp");
            if (queueTimestamp != null) {
                scheduler.setQueueTimestamp(queueTimestamp.getAsLong());
            }
            JsonPrimitive buildKey = oo.getAsJsonPrimitive("buildKey");
            if (buildKey != null) {
                scheduler.setBuildKey(buildKey.getAsString());
            }
            return scheduler;
        }
        throw new IllegalArgumentException("Wrong format!");
    }
    private final String uuid;
    private final String resultId;
    private final String bambooServer;
    private final String sidekick;
    private String taskARN;
    private final Configuration configuration;
    private long queueTimestamp = -1;
    private final List<HostFolderMapping> hostFolderMappings;
    private String buildKey = "none";

    public Scheduler(String uuid, String resultId, String server, String sidekick, 
            Configuration configuration, List<HostFolderMapping> mappings) {
        this.uuid = uuid;
        this.resultId = resultId;
        this.configuration = configuration;
        this.bambooServer = server;
        this.sidekick = sidekick;
        this.hostFolderMappings = mappings;
    }

    public String getUuid() {
        return uuid;
    }

    public String getResultId() {
        return resultId;
    }

    public String getBambooServer() {
        return bambooServer;
    }

    public String getSidekick() {
        return sidekick;
    }


    public Configuration getConfiguration() {
        return configuration;
    }

    public String getTaskARN() {
        return taskARN;
    }

    public void setTaskARN(String taskARN) {
        this.taskARN = taskARN;
    }

    public long getQueueTimestamp() {
        return queueTimestamp;
    }

    public void setQueueTimestamp(long queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
    }

    public String getBuildKey() {
        return buildKey;
    }

    public void setBuildKey(String buildKey) {
        this.buildKey = buildKey;
    }

    public List<HostFolderMapping> getHostFolderMappings() {
        return hostFolderMappings;
    }

    private static class HostFolderMappingImpl implements HostFolderMapping {

        private final String name;
        private final String hostPath;
        private final String containerPath;

        public HostFolderMappingImpl(String name, String hostPath, String containerPath) {
            this.name = name;
            this.hostPath = hostPath;
            this.containerPath = containerPath;
        }

        @Override
        public String getVolumeName() {
            return name;
        }

        @Override
        public String getHostPath() {
            return hostPath;
        }

        @Override
        public String getContainerPath() {
            return containerPath;
        }
    }
}
