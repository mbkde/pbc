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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.Date;
import org.junit.After;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AgentsThrottledTest {

    private AgentsThrottled agentsThrottled;
    private final DateTime dateTime = mock(DateTime.class);

    @BeforeEach
    public void setUp() {
        reset(dateTime);
        agentsThrottled = new AgentsThrottled(dateTime);
    }

    @After
    public void tearDown() {
        reset(dateTime);
    }

    @Test
    public void addSingleKey() {
        agentsThrottled.add("key");
        assertEquals(1, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void addSameKeyMultipleTimes() {
        agentsThrottled.add("key");
        agentsThrottled.add("key");
        agentsThrottled.add("key");
        assertEquals(1, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void addMultipleKeys() {
        agentsThrottled.add("key1");
        agentsThrottled.add("key2");
        agentsThrottled.add("key3");
        assertEquals(3, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void removeExistingKey() {
        String key = "key";
        agentsThrottled.add(key);
        assertEquals(1, agentsThrottled.getTotalAgentsThrottled());
        agentsThrottled.remove(key);
        assertEquals(0, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void removeNonexistentKey() {
        assertEquals(0, agentsThrottled.getTotalAgentsThrottled());
        agentsThrottled.remove("key");
        assertEquals(0, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void testCorrectNumberOfAgentsMarkedAsThrottledGivenSpecificNumberOfMinutes() {
        int minutes = 5;
        long fiveMinutesAgo = overXMinsAgoMilliseconds(minutes);
        when(dateTime.getCurrentTime()).thenReturn(fiveMinutesAgo);
        agentsThrottled.add("key1");
        agentsThrottled.add("key2");
        agentsThrottled.add("key3");
        when(dateTime.getCurrentTime()).thenReturn(new Date().getTime());
        assertEquals(3, agentsThrottled.numAgentsThrottledLongerThanMinutes(minutes));
    }

    @Test
    public void testAgentsNotMarkedAsThrottledForLongerThanItHas() {
        long fiveMinutesAgo = overXMinsAgoMilliseconds(5);
        when(dateTime.getCurrentTime()).thenReturn(fiveMinutesAgo);
        agentsThrottled.add("key1");
        when(dateTime.getCurrentTime()).thenReturn(new Date().getTime());
        assertEquals(0, agentsThrottled.numAgentsThrottledLongerThanMinutes(6));
    }

    @Test
    public void addingAgentAgainShouldNotImpactStartThrottledTime() {
        long fiveMinutesAgo = overXMinsAgoMilliseconds(5);
        when(dateTime.getCurrentTime()).thenReturn(fiveMinutesAgo);
        agentsThrottled.add("key1");
        when(dateTime.getCurrentTime()).thenReturn(new Date().getTime());
        agentsThrottled.add("key1");
        assertEquals(1, agentsThrottled.numAgentsThrottledLongerThanMinutes(5));
    }

    @Test
    public void onlyAgentsThrottledLongEnoughShouldBeReturned() {
        long fiveMinutesAgo = overXMinsAgoMilliseconds(5);
        long threeMinutesAgo = overXMinsAgoMilliseconds(3);
        when(dateTime.getCurrentTime()).thenReturn(fiveMinutesAgo);
        agentsThrottled.add("key1");
        when(dateTime.getCurrentTime()).thenReturn(threeMinutesAgo);
        agentsThrottled.add("key2");
        when(dateTime.getCurrentTime()).thenReturn(new Date().getTime());
        assertEquals(1, agentsThrottled.numAgentsThrottledLongerThanMinutes(5));
    }

    // return time just over x minutes ago
    private long overXMinsAgoMilliseconds(int minutes) {
        return new Date().getTime() - ((long) minutes * 60 * 1000) - 1;
    }
}
