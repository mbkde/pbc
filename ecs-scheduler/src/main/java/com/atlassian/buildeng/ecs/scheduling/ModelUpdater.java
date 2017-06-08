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

public interface ModelUpdater {

    void updateModel(DockerHosts hosts, State req);

    void scaleDown(DockerHosts hosts, long futureReservations);

    public static final class State {

        private final long lackingCPU;
        private final long lackingMemory;
        private final boolean someDiscarded;
        private final long futureReservations;

        State(long lackingCPU, long lackingMemory, boolean someDiscarded, long futureReservations) {
            this.lackingCPU = lackingCPU;
            this.lackingMemory = lackingMemory;
            this.someDiscarded = someDiscarded;
            this.futureReservations = futureReservations;
        }

        public long getLackingCPU() {
            return lackingCPU;
        }

        public long getLackingMemory() {
            return lackingMemory;
        }

        public boolean isSomeDiscarded() {
            return someDiscarded;
        }

        public long getFutureReservations() {
            return futureReservations;
        }

    }
}
