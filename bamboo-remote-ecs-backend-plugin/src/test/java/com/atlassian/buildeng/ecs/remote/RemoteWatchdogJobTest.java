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

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.atlassian.buildeng.ecs.shared.StoppedState;
import com.sun.jersey.api.client.Client;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;


public class RemoteWatchdogJobTest {
    @Test
    public void testRetrieveStoppedTasksBatched() throws Exception {
        RemoteWatchdogJob watchdogJob = new RemoteWatchdogJobMock();
        GlobalConfiguration globalConfig = mock(GlobalConfiguration.class);
        HashMap jobData = new HashMap();
        jobData.put("globalConfiguration", globalConfig);

        List<StoppedState> tasks = watchdogJob.retrieveStoppedTasksByArn(Collections.nCopies(85, ""), jobData);
        assertEquals(85, tasks.size());
        tasks = watchdogJob.retrieveStoppedTasksByArn(Collections.nCopies(40, ""), jobData);
        assertEquals(40, tasks.size());
        tasks = watchdogJob.retrieveStoppedTasksByArn(Collections.nCopies(10, ""), jobData);
        assertEquals(10, tasks.size());
    }

    public static class RemoteWatchdogJobMock extends RemoteWatchdogJob {

        @Override
        protected List<StoppedState> queryStoppedTasksByArn(GlobalConfiguration globalConfig,
                Client client,
                List<String> arns) {
            return arns.stream().map(arn -> new StoppedState(arn, "", "")).collect(toList());
        }
    }
}
