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

package com.atlassian.buildeng.isolated.docker.jmx;

public interface AgentCountsMBean {
    
    long getQueuedAgentsCount();
    
    long getScheduledAgentsCount();
    
    long getActiveAgentsCount();
    
    long getCancelledAgentsCount();
    
    long getTimedOutAgentsCount();
    
    long getFailedAgentsCount();

    long getAgentsThrottledTotalGauge();

    long getThrottledFor5MinutesGauge();

    long getThrottledFor10MinutesGauge();

    long getThrottledFor15MinutesGauge();

    long getThrottledFor20MinutesGauge();

    long getThrottledFor25MinutesGauge();

    long getThrottledFor30MinutesGauge();
}
