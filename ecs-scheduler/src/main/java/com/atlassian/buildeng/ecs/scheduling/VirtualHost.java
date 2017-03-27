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

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class VirtualHost implements Host {
    private int remainingMemory;
    private int remainingCpu;
    private final int registeredMemory;
    private final int registeredCpu;
    private final Set<UUID> requestIds = new HashSet<>();

    public VirtualHost(int registeredMemory, int registeredCpu) {
        this.registeredMemory = registeredMemory;
        this.registeredCpu = registeredCpu;
        this.remainingCpu = registeredCpu;
        this.remainingMemory = registeredMemory;
    }

    @Override
    public int getRemainingMemory() {
        return remainingMemory;
    }

    @Override
    public int getRemainingCpu() {
        return remainingCpu;
    }

    @Override
    public int getRegisteredMemory() {
        return registeredMemory;
    }

    @Override
    public int getRegisteredCpu() {
        return registeredCpu;
    }

    public void reduceAvailablity(int memory, int cpu, UUID requestId) {
        remainingMemory = remainingMemory - memory;
        remainingCpu = remainingCpu - cpu;
        requestIds.add(requestId);
    }
    public void increaseAvailability(int memory, int cpu, UUID requestId) {
        remainingMemory = remainingMemory + memory;
        remainingCpu = remainingCpu + cpu;
        requestIds.remove(requestId);

    }

    public boolean servedRequest(UUID requestId) {
        return requestIds.contains(requestId);
    }

    public boolean isEmpty() {
        return getRegisteredCpu() == getRemainingCpu() && getRegisteredMemory() == getRemainingMemory();
    }

    public static Optional<VirtualHost> findByRequest(Collection<VirtualHost> hosts, UUID requestId) {
        return hosts.stream()
                .filter((VirtualHost t) -> t.servedRequest(requestId))
                .findFirst();
    }

}
