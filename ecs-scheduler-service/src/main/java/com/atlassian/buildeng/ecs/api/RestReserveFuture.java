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

package com.atlassian.buildeng.ecs.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

public class RestReserveFuture {

    public static RestReserveFuture fromJson(String v) {
        JsonParser p = new JsonParser();
        JsonElement obj = p.parse(v);
        if (obj.isJsonObject()) {
            JsonObject oo = obj.getAsJsonObject();
            JsonPrimitive buildKey = oo.getAsJsonPrimitive("buildKey");
            JsonArray resultKeys = oo.getAsJsonArray("resultKeys");
            JsonPrimitive cpu = oo.getAsJsonPrimitive("cpu");
            JsonPrimitive memory = oo.getAsJsonPrimitive("memory");
            if (buildKey == null || resultKeys == null || cpu == null || memory == null) {
                throw new IllegalArgumentException("Wrong format!");
            }
            ArrayList<String> rk = new ArrayList<>();
            resultKeys.forEach((JsonElement t) -> {
                rk.add(t.getAsString());
            });
            return new RestReserveFuture(buildKey.getAsString(), rk, cpu.getAsLong(), memory.getAsLong());
        }
        throw new IllegalArgumentException("Wrong format!");

    }

    private final String buildKey;
    private final List<String> resultKeys;
    private final long cpuReservation;
    private final long memoryReservation;

    public RestReserveFuture(String buildKey, List<String> resultKeys, long cpuReservation, long memoryReservation) {
        this.buildKey = buildKey;
        this.resultKeys = resultKeys;
        this.cpuReservation = cpuReservation;
        this.memoryReservation = memoryReservation;
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

    
}
