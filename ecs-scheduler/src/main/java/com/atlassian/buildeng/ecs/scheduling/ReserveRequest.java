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

import java.util.List;
import java.util.Objects;

public class ReserveRequest {
    private final String buildKey;
    private final long cpuReservation;
    private final long memoryReservation;
    private final long creationTimestamp;
    private final List<String> resultKeys;

    public ReserveRequest(String groupIdentifier, List<String> resultKeys, long cpuReservation, long memoryReservation) {
        this.buildKey = groupIdentifier;
        this.cpuReservation = cpuReservation;
        this.memoryReservation = memoryReservation;
        this.resultKeys = resultKeys;
        this.creationTimestamp = System.currentTimeMillis();
    }

    public String getBuildKey() {
        return buildKey;
    }

    public List<String> getResultKeys() {
        return resultKeys;
    }
    
    public long getCpuReservation() {
        return cpuReservation;
    }

    public long getMemoryReservation() {
        return memoryReservation;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.buildKey);
        hash = 31 * hash + (int) (this.cpuReservation ^ (this.cpuReservation >>> 32));
        hash = 31 * hash + (int) (this.memoryReservation ^ (this.memoryReservation >>> 32));
        hash = 31 * hash + Objects.hashCode(this.resultKeys);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ReserveRequest other = (ReserveRequest) obj;
        if (this.cpuReservation != other.cpuReservation) {
            return false;
        }
        if (this.memoryReservation != other.memoryReservation) {
            return false;
        }
        if (!Objects.equals(this.buildKey, other.buildKey)) {
            return false;
        }
        return Objects.equals(this.resultKeys, other.resultKeys);
    }



}
