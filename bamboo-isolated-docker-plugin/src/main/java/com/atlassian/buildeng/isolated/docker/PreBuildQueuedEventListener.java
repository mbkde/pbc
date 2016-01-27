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

import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.events.BuildQueuedEvent;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.event.api.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * this class is an event listener because preBuildQueuedAction requires restart
 * of bamboo when re-deployed.
 */
public class PreBuildQueuedEventListener  {

    private final IsolatedAgentService isolatedAgentService;
    private final Logger LOG = LoggerFactory.getLogger(PreBuildQueuedEventListener.class);
    private final ErrorUpdateHandler errorUpdateHandler;


    public PreBuildQueuedEventListener(IsolatedAgentService isolatedAgentService, ErrorUpdateHandler errorUpdateHandler) {
        this.isolatedAgentService = isolatedAgentService;
        this.errorUpdateHandler = errorUpdateHandler;
    }

    @EventListener
    public void call(BuildQueuedEvent event) throws InterruptedException, Exception {
        BuildContext buildContext = event.getContext();
        Configuration config = Configuration.forBuildContext(buildContext);
        if (config.isEnabled()) {
            try {
                buildContext.getBuildResult().getCustomBuildData().put(Constants.RESULT_TIME_QUEUED, "" + System.currentTimeMillis());
                isolatedAgentService.startInstance(new IsolatedDockerAgentRequest(config.getDockerImage(), buildContext.getBuildResultKey()));
            } catch (Exception ex) {
                errorUpdateHandler.recordError(buildContext.getResultKey(), "Build was not queued due to error", ex);
                //TODO terminate the build? how?
            }
        }
    }
}
