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
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.GetConsoleOutputRequest;
import com.amazonaws.services.ec2.model.GetConsoleOutputResult;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.GetLogEventsResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.OutputLogEvent;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsLogs {

    private static final Logger logger = LoggerFactory.getLogger(AwsLogs.class);

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
                    if (t.getTimestamp() != null) {
                        writer.write(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(t.getTimestamp())));
                        writer.write(" ");
                    }
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
            writer.println(ex.getLocalizedMessage());
        } finally {
            writer.flush();
        }
    }

    /**
     * returns an object of Driver with configuration for awslogs logging driver if configured,
     * if not, returns null.
     */
    public static Driver getAwsLogsDriver(ECSConfiguration config) {
        String driver = config.getLoggingDriver();
        if ("awslogs".equals(driver)) {
            return new Driver(config.getLoggingDriverOpts().get("awslogs-region"),
                    config.getLoggingDriverOpts().get("awslogs-group"),
                    config.getLoggingDriverOpts().get("awslogs-stream-prefix"));
        }
        return null;
    }

    public static void logEC2InstanceOutputToCloudwatch(String t, ECSConfiguration configuration) {
        AwsLogs.Driver driver = AwsLogs.getAwsLogsDriver(configuration);
        if (driver != null && driver.getRegion() != null && driver.getLogGroupName() != null) {
            try {
                AmazonEC2 client = AmazonEC2ClientBuilder.defaultClient();
                GetConsoleOutputResult result = client.getConsoleOutput(new GetConsoleOutputRequest(t));
                AWSLogsClient logs = new AWSLogsClient().withRegion(Regions.fromName(driver.getRegion()));
                // t (ec2 instance id) should be unique within reason
                final String logStreamName = "pbc-ec2-instance-stale/" + t;
                logs.createLogStream(new CreateLogStreamRequest(driver.getLogGroupName(), logStreamName));
                logs.putLogEvents(new PutLogEventsRequest().withLogGroupName(driver.getLogGroupName()).withLogStreamName(logStreamName).withLogEvents(new InputLogEvent().withMessage(result.getDecodedOutput()).withTimestamp(System.currentTimeMillis())));
            } catch (Exception th) {
                //we are fine swallowing any errors, has no direct influence on proper function.
                logger.error("failed to retrieve ec2 instance logs or send them to cloudwatch", th);
            }
        }
    }

    public static class Driver {

        private final String region;
        private final String logGroupName;
        private final String streamPrefix;

        private Driver(String region, String logGroupName, String streamPrefix) {
            this.region = region;
            this.logGroupName = logGroupName;
            this.streamPrefix = streamPrefix;
        }

        public String getRegion() {
            return region;
        }

        public String getLogGroupName() {
            return logGroupName;
        }

        public String getStreamPrefix() {
            return streamPrefix;
        }

    }
}
