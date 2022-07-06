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

package com.atlassian.buildeng.ecs.remote;

import static com.atlassian.buildeng.ecs.remote.ECSIsolatedAgentServiceImpl.createClient;

import com.atlassian.buildeng.ecs.remote.rest.ArnStoppedState;
import com.atlassian.buildeng.ecs.shared.AbstractWatchdogJob;
import com.atlassian.buildeng.ecs.shared.StoppedState;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.quartz.DisallowConcurrentExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class RemoteWatchdogJob extends AbstractWatchdogJob {

    private static final Logger logger = LoggerFactory.getLogger(RemoteWatchdogJob.class);
    // There is a limit on how many ARNs we can put in the query parameter before getting a HTTP 414 URI Too Long
    private static int MAXIMUM_ARNS_TO_QUERY = 40;

    @Override
    protected List<StoppedState> retrieveStoppedTasksByArn(
            List<String> arns, Map<String, Object> jobDataMap) throws Exception {
        GlobalConfiguration globalConfig = getService(GlobalConfiguration.class, "globalConfiguration", jobDataMap);
        Client client = createClient();

        List<StoppedState> tasks = new ArrayList<>();

        for (int i = 0; i < arns.size() / MAXIMUM_ARNS_TO_QUERY; i++) {
            List<String> nextNArns = arns.subList(i * MAXIMUM_ARNS_TO_QUERY, (i + 1) * MAXIMUM_ARNS_TO_QUERY);
            tasks.addAll(queryStoppedTasksByArn(globalConfig, client, nextNArns));
        }

        List<String> lastNArns = arns.subList(
                MAXIMUM_ARNS_TO_QUERY * (arns.size() / MAXIMUM_ARNS_TO_QUERY), arns.size());
        tasks.addAll(queryStoppedTasksByArn(globalConfig, client, lastNArns));

        return tasks;
    }

    protected List<StoppedState> queryStoppedTasksByArn(
            GlobalConfiguration globalConfig, Client client, List<String> arns) {
        WebResource resource = client.resource(globalConfig.getCurrentServer() + "/rest/scheduler/stopped");
        for (String arn : arns) {
            //!! each call to resource returning WebResource is returning new instance
            resource = resource.queryParam("arn", arn);
        }
        List<ArnStoppedState> result = resource
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .get(new GenericType<List<ArnStoppedState>>(){});
        return result.stream()
                .map((ArnStoppedState t) -> new StoppedState(t.getArn(), t.getContainerArn(), t.getReason()))
                .collect(Collectors.toList());
    }
}
