/*
 * Copyright 2016 Atlassian.
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

package com.atlassian.buildeng.spi.isolated.docker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IsolatedDockerAgentResult {

    private final List<String> errors = new ArrayList<>();
    private final Map<String, String> customData = new HashMap<>();
    private boolean retry = false;

    public IsolatedDockerAgentResult() {
    }

    public IsolatedDockerAgentResult withError(String error) {
        errors.add(error);
        return this;
    }
    
    
    public IsolatedDockerAgentResult withCustomResultData(String key, String value) {
        customData.put(key, value);
        return this;
    }
    
    public IsolatedDockerAgentResult withRetryRecoverable() {
        retry = true;
        return this;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public Map<String, String> getCustomResultData() {
        return customData;
    }
    
    /**
     * when the request fails, but the state of the docker cloud suggests that
     * later retry might succeed. Eg. when the docker containers are in process
     * of scaling up.
     * @return 
     */
    public boolean isRetryRecoverable() {
        return retry;
    }
    
}
