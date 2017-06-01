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
import java.net.URL;
import java.util.Map;

/**
 * event intended to be sent to datadog via the monitoring plugin.
 * @author mkleint
 */
public final class DockerAgentRemoteFailEvent {

    private final String errorMessage;
    private final Key key;
    private final String taskArn;
    private final String containerArn;
    private final Map<String, URL> markdownLinks;

    static boolean ddmarkdown = Boolean.parseBoolean(System.getProperty("pbc.event.tostring.datadog", "true"));


    public DockerAgentRemoteFailEvent(String errorMessage, Key key, String taskArn, String containerArn, Map<String, URL> markdownLinks) {
        this.errorMessage = errorMessage;
        this.key = key;
        this.taskArn = taskArn;
        this.containerArn = containerArn;
        this.markdownLinks = markdownLinks;
    }

    @Override
    public String toString() {
        if (ddmarkdown) {
            //http://docs.datadoghq.com/guides/markdown/
            return "%%% \\n" +
                    "Key:**" + key.getKey() + "** Task ARN:" + taskArn + "\\n" +
                    "Container ARN:" + containerArn + "\\n" +
                    "Container logs: " + generateMarkdownLinks(markdownLinks) + "\\n" +
                    escape(errorMessage) + "\\n" +
                    "\\n %%%";
        }
        return "DockerAgentRemoteFailEvent{task=" + taskArn +  ", container=" + containerArn + ", key=" + key  + ",containerLogs=" + markdownLinks +  ",message=" + errorMessage +  "}";
    }

    private String generateMarkdownLinks(Map<String, URL> markdownLinks) {
        StringBuilder sb = new StringBuilder();
        markdownLinks.forEach((String t, URL u) -> {
            sb.append("[").append(t).append("](").append(u.toString()).append(") ");
        });
        return sb.toString();
    }

    static String escape(String text) {
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
                   .replace("}", "\\}")
                ;
    }
}
