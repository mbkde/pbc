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
package com.atlassian.buildeng.ecs.scheduling;

public enum ECSInstance {
    M4_XLARGE(4096, 16050, "m4.xlarge"),
    M4_4XLARGE(16384, 64419, "m4.4xlarge"),
    M4_10XLARGE(40960, 161186, "m4.10xlarge"),
    M4_16XLARGE(65536, 257955, "m4.16xlarge"),
    M5_12XLARGE(49152, 185198, "m5.12xlarge"),
    I3_8XLARGE(32768, 245731, "i3.8xlarge"),
    I3_16XLARGE(65536, 491683,"i3.16xlarge");

    private int cpu;
    private int memory;
    private String name;
    static ECSInstance DEFAULT_INSTANCE = M4_4XLARGE;

    ECSInstance(int cpu, int memory, String name) {
        this.cpu = cpu;
        this.memory = memory;
        this.name = name;
    }

    static ECSInstance fromName(String name) {
        for (ECSInstance x: ECSInstance.values()) {
            if (x.name.equals(name)) return x;
        }
        return DEFAULT_INSTANCE;
    }


    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

    public String getName() {
        return name;
    }
}

