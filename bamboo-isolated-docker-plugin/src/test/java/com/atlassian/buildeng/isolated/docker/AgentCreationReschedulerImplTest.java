/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.ResultKey;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class AgentCreationReschedulerImplTest {

    private final BuildQueueManager buildQueueManager = mock(BuildQueueManager.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private AgentCreationReschedulerImpl agentCreationReschedulerImpl;

    @Before
    public void setUp() {
        agentCreationReschedulerImpl = new AgentCreationReschedulerImpl(eventPublisher, buildQueueManager);
    }

    @Test
    public void rescheduleWillReturnFalseOnTooManyRescheduleAttempts() {
        RetryAgentStartupEvent event = new RetryAgentStartupEvent(null, mockBuildContext(), 99999, UUID.randomUUID());
        assertFalse(agentCreationReschedulerImpl.reschedule(event));
    }

    @Test
    public void rescheduleWillReturnTrueOnSuccessfulReschedule() {
        RetryAgentStartupEvent event = new RetryAgentStartupEvent(null, mockBuildContext(), 0, UUID.randomUUID());
        assertTrue(agentCreationReschedulerImpl.reschedule(event));
    }

    private BuildContext mockBuildContext() {
        BuildContext context = mock(BuildContext.class);
        ResultKey resultKey = mock(ResultKey.class);
        CurrentResult currentResult = mock(CurrentResult.class);
        Map<String, String> customBuildData = new HashMap<>();
        when(context.getResultKey()).thenReturn(resultKey);
        when(context.getCurrentResult()).thenReturn(currentResult);
        when(currentResult.getCustomBuildData()).thenReturn(customBuildData);
        return context;
    }

}
