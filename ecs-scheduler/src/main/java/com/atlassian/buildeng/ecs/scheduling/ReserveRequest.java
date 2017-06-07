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

package com.atlassian.buildeng.ecs.scheduling;

public class ReserveRequest {
    private final String groupIdentifier;
    private final String uniqueIndentifier;
    private final long cpuReservation;
    private final long memoryReservation;

    public ReserveRequest(String groupIdentifier, String uniqueIndentifier, long cpuReservation, long memoryReservation) {
        this.groupIdentifier = groupIdentifier;
        this.uniqueIndentifier = uniqueIndentifier;
        this.cpuReservation = cpuReservation;
        this.memoryReservation = memoryReservation;
    }

    public String getGroupIdentifier() {
        return groupIdentifier;
    }

    public String getUniqueIndentifier() {
        return uniqueIndentifier;
    }

    public long getCpuReservation() {
        return cpuReservation;
    }

    public long getMemoryReservation() {
        return memoryReservation;
    }


}
