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
package com.atlassian.buildeng.simple.backend;

import org.apache.commons.lang3.SystemUtils;

/**
 *
 * @author mkleint
 */
public class ExecutablePathUtils {
    
    public static String getDockerBinaryPath() {
        if (SystemUtils.IS_OS_LINUX) {
            return "/usr/bin/docker";
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            return "/usr/local/bin/docker";
        } else {
            throw new IllegalStateException("Unsupported platform");
        }
    }
    
    public static String getDockerComposeBinaryPath() {
        if (SystemUtils.IS_OS_LINUX) {
            return "/usr/local/bin/docker-compose";
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            return "/usr/local/bin/docker-compose";
        } else {
            throw new IllegalStateException("Unsupported platform");
        }
    }
}
