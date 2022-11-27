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

package com.atlassian.buildeng.ecs.resources;

import com.atlassian.buildeng.ecs.logs.AwsLogs;
import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import java.io.OutputStream;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@Path("/rest/logs")
public class LogsResource {

    private final ECSConfiguration configuration;

    @Inject
    public LogsResource(ECSConfiguration configuration) {
        this.configuration = configuration;
    }

    static final String PARAM_CONTAINER = "containerName";
    static final String PARAM_TASK_ARN = "taskArn";

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAwsLogs(@QueryParam(PARAM_CONTAINER) String containerName,
            @QueryParam(PARAM_TASK_ARN) String taskArn) {
        if (containerName == null || taskArn == null) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(PARAM_CONTAINER + " and " + PARAM_TASK_ARN + " are mandatory")
                    .build();
        }
        AwsLogs.Driver driver = AwsLogs.getAwsLogsDriver(configuration);
        if (driver != null) {
            if (driver.getRegion() == null || driver.getLogGroupName() == null || driver.getStreamPrefix() == null) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("For awslogs docker log driver, all of 'awslogs-region', 'awslogs-group' and 'awslogs-stream-prefix' have to be defined.")
                        .build();
            }

            StreamingOutput stream = (OutputStream os) -> {
                AwsLogs.writeTo(os,
                        driver.getLogGroupName(),
                        driver.getRegion(),
                        driver.getStreamPrefix(),
                        containerName,
                        taskArn);
            };
            return Response.ok(stream).build();
        }
        return Response.ok("No Logs present for " + containerName + " and " + taskArn).build();
    }
}
