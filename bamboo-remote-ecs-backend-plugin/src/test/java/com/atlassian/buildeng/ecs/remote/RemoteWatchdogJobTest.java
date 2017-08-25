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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atlassian.buildeng.ecs.shared.StoppedState;
import com.sun.jersey.api.client.Client;
import org.junit.Test;

import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;

import static org.junit.Assert.assertEquals;

public class RemoteWatchdogJobTest {
    @Test
    public void testRetrieveStoppedTasksBatched() throws Exception {
        RemoteWatchdogJob watchdogJob = new RemoteWatchdogJobMock();

        List<StoppedState> tasks = watchdogJob.retrieveStoppedTasksByArn(Collections.nCopies(85, ""), new HashMap<>());
        assertEquals(85, tasks.size());
        tasks = watchdogJob.retrieveStoppedTasksByArn(Collections.nCopies(40, ""), new HashMap<>());
        assertEquals(40, tasks.size());
        tasks = watchdogJob.retrieveStoppedTasksByArn(Collections.nCopies(10, ""), new HashMap<>());
        assertEquals(10, tasks.size());
    }

    public static class RemoteWatchdogJobMock extends RemoteWatchdogJob {
        GlobalConfiguration globalConfig = mock(GlobalConfiguration.class);

        @Override
        protected <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
            return type.cast(globalConfig);
        }

        @Override
        protected List<StoppedState> queryStoppedTasksByArn(
                GlobalConfiguration globalConfig, Client client, List<String> arns) {
            return arns.stream().map(arn -> new StoppedState(arn, "", "")).collect(toList());
        }
    }
}
