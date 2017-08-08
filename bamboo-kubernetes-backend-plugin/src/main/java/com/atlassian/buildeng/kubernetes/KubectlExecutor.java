/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubectlExecutor {
    private static final Logger logger = LoggerFactory.getLogger(KubectlExecutor.class);

    private static final String KUBECTL_EXECUTABLE = System.getProperty("pbc.kubectl.path", "kubectl");

    /**
     * Takes the pod definition file and runs kubectl to start the pod.
     */
    public static Result startPod(File podFile) throws InterruptedException, IOException, JsonIOException {
        ProcessBuilder pb = new ProcessBuilder(KUBECTL_EXECUTABLE,
                "create", "-f", podFile.getAbsolutePath(), "-o", "json");
        pb.redirectErrorStream(true);
        //kubectl requires HOME env to find the config, but the bamboo server jvm might nto have it setup.
        pb.environment().put("HOME", System.getProperty("user.home"));
        Process process = pb.start();
        JsonParser parser = new JsonParser();
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            sb.append(line).append("\n");
            line = br.readLine();
        }
        JsonElement responseBody = null;
        try {
            responseBody = parser.parse(sb.toString());
        } catch (JsonParseException ex) {
            logger.debug("Failed to parse response, likely an error", ex);
        }
        int ret = process.waitFor();
        return new Result(responseBody, ret, sb.toString());
    }

    public static final class Result {
        private final JsonElement response;
        private final String rawResponse;
        private final int resultCode;

        private Result(JsonElement response, int resultCode, String rawResponse) {
            this.response = response;
            this.resultCode = resultCode;
            this.rawResponse = rawResponse;
        }

        @Nullable
        public JsonElement getResponse() {
            return response;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public int getResultCode() {
            return resultCode;
        }

        /**
         * The created pod's uid if returned.
         */
        public String getPodUid() {
            if (response != null) {
                JsonObject metadata = response.getAsJsonObject().getAsJsonObject("metadata");
                return metadata.getAsJsonPrimitive("uid").getAsString();
            }
            return "";
        }

        /**
         * The created pod's name if returned.
         */
        public String getPodName() {
            if (response != null) {
                JsonObject metadata = response.getAsJsonObject().getAsJsonObject("metadata");
                return metadata.getAsJsonPrimitive("name").getAsString();
            }
            return "";
        }
    }
}
