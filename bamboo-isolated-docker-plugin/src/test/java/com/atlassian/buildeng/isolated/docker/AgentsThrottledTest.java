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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AgentsThrottledTest {

    private AgentsThrottled agentsThrottled;
    private static final double RETRY_DELAY_SECONDS = Constants.RETRY_DELAY.getSeconds();
    private static final double RETRIES_EACH_MINUTE = 60 / RETRY_DELAY_SECONDS;

    @BeforeEach
    public void setUp() {
        agentsThrottled = new AgentsThrottled();
    }

    @Test
    public void addSingleKey() {
        agentsThrottled.add("key");
        Assertions.assertEquals(1, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void addSameKeyMultipleTimes() {
        agentsThrottled.add("key");
        agentsThrottled.add("key");
        agentsThrottled.add("key");
        Assertions.assertEquals(1, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void addMultipleKeys() {
        agentsThrottled.add("key1");
        agentsThrottled.add("key2");
        agentsThrottled.add("key3");
        Assertions.assertEquals(3, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void removeExistingKey() {
        String key = "key";
        agentsThrottled.add(key);
        Assertions.assertEquals(1, agentsThrottled.getTotalAgentsThrottled());
        agentsThrottled.remove(key);
        Assertions.assertEquals(0, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void removeNonexistentKey() {
        Assertions.assertEquals(0, agentsThrottled.getTotalAgentsThrottled());
        agentsThrottled.remove("key");
        Assertions.assertEquals(0, agentsThrottled.getTotalAgentsThrottled());
    }

    @Test
    public void testCorrectNumberOfAgentsMarkedAsThrottledGivenSpecificNumberOfMinutes() {
        int minutes = 5;
        double retriesFor5Minutes = RETRIES_EACH_MINUTE * minutes;
        for (int i = 0; i < retriesFor5Minutes; i++) {
            agentsThrottled.add("key1");
            agentsThrottled.add("key2");
            agentsThrottled.add("key3");
        }
        Assertions.assertEquals(3, agentsThrottled.numAgentsThrottledLongerThanMinutes(minutes));
    }

    @Test
    public void testAgentsNotMarkedAsThrottledForLongerThanItHas() {
        int minutes = 5;
        double retriesFor5Minutes = RETRIES_EACH_MINUTE * minutes;
        for (int i = 0; i < retriesFor5Minutes - 1; i++) {
            agentsThrottled.add("key1");
        }
        Assertions.assertEquals(0, agentsThrottled.numAgentsThrottledLongerThanMinutes(minutes));
    }
}
