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

/**
 * event intended to be sent to datadog via the monitoring plugin.
 * @author mkleint
 */
public final class DockerAgentRemoteSilentRetryEvent {

    private final String errorMessage;
    private final Key key;
    private final String taskArn;
    private final String containerArn;


    public DockerAgentRemoteSilentRetryEvent(String errorMessage, Key key, String taskArn, String containerArn) {
        this.errorMessage = errorMessage;
        this.key = key;
        this.taskArn = taskArn;
        this.containerArn = containerArn;
    }

    @Override
    public String toString() {
        if (DockerAgentRemoteFailEvent.ddmarkdown) {
            //http://docs.datadoghq.com/guides/markdown/
            return "%%% \\n" +
                    "Key:**" + key.getKey() + "** Task ARN:" + taskArn + "\\n" +
                    "Container ARN:" + containerArn + "\\n" +
                    DockerAgentRemoteFailEvent.escape(errorMessage) + "\\n" +
                    "\\n %%%";
        }

        return "DockerAgentRemoteSilentRetryEvent{" + "errorMessage=" + errorMessage + ", key=" + key + ", task=" + taskArn + ", container=" + containerArn + "}";
    }
    
}
