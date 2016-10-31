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

package com.atlassian.buildeng.ecs.remote;

import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import java.util.Collections;
import java.util.List;

public class ECSIsolatedAgentServiceImpl implements IsolatedAgentService, LifecycleAware {

    private final GlobalConfiguration globalConfiguration;

    public ECSIsolatedAgentServiceImpl(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }



    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {
    }

}
