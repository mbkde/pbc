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

package com.atlassian.buildeng.isolated.docker.events;

import com.atlassian.bamboo.Key;
import com.atlassian.buildeng.kubernetes.GlobalConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.events.DockerAgentEvent;
import java.net.URL;
import java.util.Map;

public class DockerAgentKubeEvent extends DockerAgentEvent {

    private final String errorMessage;
    private final Key key;
    private final String podName;
    private final Map<String, URL> markdownLinks;
    private final GlobalConfiguration config;

    /**
     * Event sent to Datadog for when a pod fails to queue, for any reason.
     */
    public DockerAgentKubeEvent(String errorMessage,
            Key key,
            String podName,
            Map<String, URL> markdownLinks,
            GlobalConfiguration config) {
        this.errorMessage = errorMessage;
        this.key = key;
        this.podName = podName;
        this.markdownLinks = markdownLinks;
        this.config = config;
    }

    @Override
    public String toString() {
        if (ddmarkdown) {
            // http://docs.datadoghq.com/guides/markdown/
            return "%%% \\n" +
                    "[Build link](" +
                    config.getBambooBaseUrl() +
                    "/browse/" +
                    key.getKey() +
                    ")\\n" +
                    "Pod name: " +
                    podName +
                    "\\n" +
                    "Container logs: " +
                    generateMarkdownLinks(markdownLinks) +
                    "\\n" +
                    escape(errorMessage) +
                    "\\n" +
                    "\\n %%%";
        }
        return this.getClass().getSimpleName() +
                "{podName=" +
                podName +
                ", key=" +
                key +
                ",containerLogs=" +
                markdownLinks +
                ",message=" +
                errorMessage +
                "}";
    }

}

