/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.yaml;

import java.util.Map;

/**
 * Storage class for storing both the raw and parsed YAML in Bandana.
 * When using this class, since Bandana does not use generics, make sure to refer to wherever the initial definition
 * of this data type is, since you will need to cast it back.
 * @param <T> The type of the value in the key-value pair of the YAML. Generally this will be {@code Object}, but if
 *           you know specifically what type it is, then type it here
 */
public class YamlStorage<T> {
    private final String rawString;
    private final Map<String, T> parsedYaml;

    public YamlStorage(String rawString, Map<String, T> parsedYaml) {
        this.rawString = rawString;
        this.parsedYaml = parsedYaml;
    }

    public Map<String, T> getParsedYaml() {
        return parsedYaml;
    }

    public String getRawString() {
        return rawString;
    }
}
