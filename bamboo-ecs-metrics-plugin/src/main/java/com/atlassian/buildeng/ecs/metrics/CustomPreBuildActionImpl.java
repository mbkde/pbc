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

package com.atlassian.buildeng.ecs.metrics;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomPreBuildAction;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CustomPreBuildActionImpl implements CustomPreBuildAction {
    // ecs uses this for unnamed host volume mounts
    private static final String AMAZON_MAGIC_VOLUME_NAME = "~internal~ecs-emptyvolume-source";
    private static final String METADATA_CONTAINER_NAME = "bamboo-agent-metadata";
    private static final String METADATA_FILE_PATH = "/buildeng/bamboo-agent-home/xml-data/build-dir/metadata";

    private final Logger logger = LoggerFactory.getLogger(CustomPreBuildActionImpl.class);
    private BuildContext buildContext;
    private BuildLoggerManager buildLoggerManager;


    public CustomPreBuildActionImpl() {
    }

    public BuildLoggerManager getBuildLoggerManager() {
        return buildLoggerManager;
    }

    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager) {
        this.buildLoggerManager = buildLoggerManager;
    }

    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() throws Exception {
        Configuration config = AccessConfiguration.forContext(buildContext);
        if (config.isEnabled()) {
            BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());
            buildLogger.addBuildLogEntry("Docker image " + config.getDockerImage() + " used to build this job");
            File metadata = new File(METADATA_FILE_PATH);
            if (metadata.isFile()) {
                try (FileReader r = new FileReader(metadata.getPath())) {
                    JsonElement topLevel = new Gson().fromJson(r, JsonElement.class);
                    if (topLevel != null && topLevel.isJsonArray()) {
                        topLevel.getAsJsonArray().forEach(jsonElement -> {
                            JsonObject curr = jsonElement.getAsJsonObject();
                            JsonElement nameObj = curr.get("name");
                            if (nameObj != null && !nameObj.getAsString().equals(METADATA_CONTAINER_NAME)
                                    && !nameObj.getAsString().equals(AMAZON_MAGIC_VOLUME_NAME)) {
                                String hash = curr.get("hash").getAsString();
                                String tag = curr.get("tag").getAsString();
                                buildLogger.addBuildLogEntry(
                                        String.format("Docker image '%s' had hash: %s", tag, hash));
                            }
                        });
                    }
                } catch (JsonSyntaxException ex) {
                    buildLogger.addBuildLogEntry("Metadata found not proper json");
                }
            } else {
                buildLogger.addBuildLogEntry("No metadata found");
            }
        }
        return buildContext;
    }

    @Override
    public ErrorCollection validate(BuildConfiguration config) {
        return new SimpleErrorCollection();
    }

}
