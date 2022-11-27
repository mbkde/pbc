/*
 * Copyright 2021 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import java.util.Date;

@BambooComponent
public class DateTime {
    /**
     * Get the time one minute ago.
     *
     * @return the time one minute ago in milliseconds
     */
    public long oneMinuteAgo() {
        return getCurrentTime() - 60 * 1000;
    }

    /**
     * Get the current time.
     *
     * @return the number of milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public long getCurrentTime() {
        return new Date().getTime();
    }
}
