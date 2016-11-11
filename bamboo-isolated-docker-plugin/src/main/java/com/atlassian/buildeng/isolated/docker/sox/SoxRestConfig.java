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

package com.atlassian.buildeng.isolated.docker.sox;

public class SoxRestConfig {

    public boolean enabled;
    public String[] whitelistPatterns;

    public SoxRestConfig() {
    }

    SoxRestConfig(boolean enabled, String[] whitelist) {
        this.enabled = enabled;
        this.whitelistPatterns = whitelist;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String[] getWhitelistPatterns() {
        return whitelistPatterns;
    }

    public void setWhitelistPatterns(String[] whitelistPatterns) {
        this.whitelistPatterns = whitelistPatterns;
    }
}