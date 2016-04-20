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
package com.atlassian.buildeng.isolated.docker.events;

import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.event.api.AsynchronousPreferred;

import java.util.Random;
import java.util.UUID;

/**
 *
 * @author mkleint
 */
@AsynchronousPreferred
public final class RetryAgentStartupEvent {

    private final int retryCount;
    private final BuildContext context;
    private final String dockerImage;
    private final UUID uniqueIdentifier;
    private final static Random rand = new Random();

    public RetryAgentStartupEvent(String dockerImage, BuildContext context) {
        this.dockerImage = dockerImage;
        this.context = context;
        this.retryCount = 0;
        this.uniqueIdentifier = UUID.randomUUID();
    }
    
    public RetryAgentStartupEvent(RetryAgentStartupEvent previousEvent) {
        this.dockerImage = previousEvent.getDockerImage();
        this.context = previousEvent.getContext();
        this.retryCount = previousEvent.getRetryCount() + 1;
        this.uniqueIdentifier = previousEvent.uniqueIdentifier;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public BuildContext getContext() {
        return context;
    }

    public UUID getUniqueIdentifier() {
        return uniqueIdentifier;
    }
}
