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

import com.amazonaws.services.ecs.model.StartTaskResult;

public class SchedulingResult {
    private final StartTaskResult startTaskResult;
    private final String containerArn;
    private final String ec2InstanceId;

    public SchedulingResult(StartTaskResult startTaskResult, String containerArn, String ec2InstanceId) {
        this.startTaskResult = startTaskResult;
        this.containerArn = containerArn;
        this.ec2InstanceId = ec2InstanceId;
    }

    public StartTaskResult getStartTaskResult() {
        return startTaskResult;
    }

    public String getContainerArn() {
        return containerArn;
    }

    public String getEc2InstanceId() {
        return ec2InstanceId;
    }
}
