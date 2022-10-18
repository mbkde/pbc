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

package com.atlassian.buildeng.ecs.shared;

public final class StoppedState {

    private final String containerArn;
    private final String reason;
    private final String arn;

    public StoppedState(String arn, String containerArn, String reason) {
        this.containerArn = containerArn;
        this.reason = reason;
        this.arn = arn;
    }



    public String getArn() {
        return arn;
    }

    public String getReason() {
        return reason;
    }

    public String getContainerArn() {
        return containerArn;
    }

}
