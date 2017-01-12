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

package com.atlassian.buildeng.ecs.logs;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.GetLogEventsResult;
import com.amazonaws.services.logs.model.OutputLogEvent;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class AwsLogs {

    private static String constructLogStream(String prefix, String containerName, String taskArn) {
        String task = taskArn.substring(taskArn.indexOf("task/") + "task/".length());
        return prefix + "/" + containerName + "/" + task;
    }


    public static void writeTo(OutputStream os, String logGroupName, String region, String prefix, String containerName, String taskArn) {
        AWSLogsClient logs = new AWSLogsClient().withRegion(Regions.fromName(region));
        PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os)));
        try {
            String logStreamName = constructLogStream(prefix, containerName, taskArn);
            GetLogEventsRequest request = new GetLogEventsRequest(logGroupName, logStreamName);
            GetLogEventsResult response = logs.getLogEvents(request);
            while (response != null) {
                response.getEvents().forEach((OutputLogEvent t) -> {
                    writer.write(t.getMessage());
                    writer.write("\n");
                });
                // why check the last token in request you ask.
                // if you don't sometimes aws will keep on sending you the same token and then trhottle you if you keep trying to get it.
                if (response.getNextForwardToken() != null && !response.getNextForwardToken().equals(request.getNextToken())) {
                    request = request.withNextToken(response.getNextForwardToken());
                    response = logs.getLogEvents(request);
                } else {
                    response = null;
                }
                writer.flush();
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace(writer);
        } finally {
            writer.flush();
        }
    }

}
