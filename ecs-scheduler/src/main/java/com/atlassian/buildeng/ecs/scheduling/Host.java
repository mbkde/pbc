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

import java.util.Comparator;

public interface Host {
    /**
     * the total amount of cpu available for docker containers on the instance
     * @return
     */
    int getRegisteredCpu();

    /**
     * the amount of memory available for docker containers on the instance
     * @return
     */
    int getRegisteredMemory();

    int getRemainingMemory();

    int getRemainingCpu();

    default boolean canRun(int requiredMemory, int requiredCpu) {
        return requiredMemory <= getRemainingMemory() && requiredCpu <= getRemainingCpu();
    }

    default boolean runningNothing() {
        return getRegisteredMemory() == getRemainingMemory() && getRegisteredCpu() == getRemainingCpu();
    }

    static <T extends Host> Comparator<T> compareByResources() {
        return (o1, o2) -> {
            if (o1.getRemainingMemory() == o2.getRemainingMemory()) {
                return Integer.compare(o1.getRemainingCpu(), o2.getRemainingCpu());
            } else {
                return Integer.compare(o1.getRemainingMemory(), o2.getRemainingMemory());
            }
        };
    }
}
