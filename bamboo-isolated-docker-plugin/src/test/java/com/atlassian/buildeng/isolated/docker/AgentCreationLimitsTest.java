/*
 * Copyright 2021 Atlassian Pty Ltd.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AgentCreationLimitsTest {
    private final GlobalConfiguration globalConfiguration = mock(GlobalConfiguration.class);
    private final DateTime dateTime = mock(DateTime.class);
    private final UUID uuid = UUID.randomUUID();
    private final RetryAgentStartupEvent event = new RetryAgentStartupEvent(null, null, 0, uuid);
    private AgentCreationLimits agentCreationLimits;

    @BeforeEach
    public void setUp() {
        reset(globalConfiguration);
        reset(dateTime);
        agentCreationLimits = new AgentCreationLimits(globalConfiguration, dateTime);
    }

    @AfterEach
    public void tearDown() {
        reset(globalConfiguration);
        reset(dateTime);
    }

    @Test
    public void creationLimitReachedWhenMaxAgentCreationZero() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(0);
        assertTrue(agentCreationLimits.creationLimitReached());
    }

    @Test
    public void creationLimitNotReached() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(1);
        assertFalse(agentCreationLimits.creationLimitReached());
    }

    @Test
    public void creationLimitReachedQueueLimitReached() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(1);
        agentCreationLimits.addToCreationQueue(event);
        assertTrue(agentCreationLimits.creationLimitReached());
    }

    @Test
    public void creationLimitReachedQueueLimitExceeded() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(1);
        agentCreationLimits.addToCreationQueue(event);
        agentCreationLimits.addToCreationQueue(event);
        assertTrue(agentCreationLimits.creationLimitReached());
    }

    @Test
    public void addMultipleEventsToCreationQueue() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(3);
        agentCreationLimits.addToCreationQueue(event);
        agentCreationLimits.addToCreationQueue(event);
        agentCreationLimits.addToCreationQueue(event);
        assertTrue(agentCreationLimits.creationLimitReached());
    }

    @Test
    public void queueIsClearedAfterOneMinute() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(1);
        agentCreationLimits.addToCreationQueue(event);
        agentCreationLimits.addToCreationQueue(event);
        agentCreationLimits.addToCreationQueue(event);
        when(dateTime.oneMinuteAgo()).thenReturn(new Date().getTime() + 60 * 1000);
        assertFalse(agentCreationLimits.creationLimitReached());
    }

    @Test
    public void removeEventFromQueue() {
        when(globalConfiguration.getMaxAgentCreationPerMinute()).thenReturn(1);
        agentCreationLimits.addToCreationQueue(event);
        agentCreationLimits.removeEventFromQueue(event);
        assertFalse(agentCreationLimits.creationLimitReached());
    }
}
