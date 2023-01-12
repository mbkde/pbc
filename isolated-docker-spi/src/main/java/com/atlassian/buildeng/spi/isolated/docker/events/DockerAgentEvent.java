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

package com.atlassian.buildeng.spi.isolated.docker.events;

import java.net.URL;
import java.util.Map;

/**
 * Base class for events sent to Datadog via the monitoring plugin.
 */
public abstract class DockerAgentEvent {

    protected static final boolean ddmarkdown =
            Boolean.parseBoolean(System.getProperty("pbc.event.tostring.datadog", "true"));

    public abstract String toString();

    /**
     * loose coupling with bamboo-monitoring-plugin, if this method is present, it will send the event to datadog.
     */
    public String getMonitoringPluginEventTag() {
        return "pbc";
    }

    protected final String generateMarkdownLinks(Map<String, URL> markdownLinks) {
        StringBuilder sb = new StringBuilder();
        markdownLinks.forEach((String t, URL u) -> {
            sb.append("[").append(t).append("](").append(u.toString()).append(") ");
        });
        return sb.toString();
    }

    protected final String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("#", "\\#")
                .replace("-", "\\-")
                .replace(".", "\\.")
                .replace("!", "\\!")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }
}
