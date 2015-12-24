/*
 * Copyright 2015 Atlassian.
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

import com.atlassian.bamboo.buildqueue.manager.CustomPreBuildQueuedAction;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPreBuildQueuedActionImpl implements CustomPreBuildQueuedAction {

    private final IsolatedAgentService isolatedAgentService;
    private BuildContext buildContext;
    private final Logger LOG = LoggerFactory.getLogger(CustomPreBuildQueuedActionImpl.class);

    public CustomPreBuildQueuedActionImpl(IsolatedAgentService isolatedAgentService) {
        this.isolatedAgentService = isolatedAgentService;
    }


    @Override
    public void init(BuildContext bc) {
        this.buildContext = bc;
    }

    @Override
    public BuildContext call() throws InterruptedException, Exception {
        LOG.info("XXX" + buildContext.getResultKey());
        String dockerConfig = findDockerConfig(buildContext.getParentBuildContext());
        if (dockerConfig != null) {
            isolatedAgentService.startInstance(new IsolatedDockerAgentRequest(dockerConfig));
        }
        return buildContext;
    }

    private String findDockerConfig(BuildContext parentBuildContext) {
        //TODO
//        Map<String, String> cc = parentBuildContext.getBuildDefinition().getCustomConfiguration().get("xxx");

        return "dockerX-one";
    }

}
