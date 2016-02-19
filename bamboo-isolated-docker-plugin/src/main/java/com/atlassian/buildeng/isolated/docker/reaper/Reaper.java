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

package com.atlassian.buildeng.isolated.docker.reaper;

import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Reaper implements LifecycleAware {
    private final PluginScheduler pluginScheduler;
    private final ExecutableAgentsHelper executableAgentsHelper;
    private final AgentManager agentManager;
    private final AgentCommandSender agentCommandSender;

    public Reaper(PluginScheduler pluginScheduler, ExecutableAgentsHelper executableAgentsHelper, AgentManager agentManager, AgentCommandSender agentCommandSender) {
        this.pluginScheduler = pluginScheduler;
        this.executableAgentsHelper = executableAgentsHelper;
        this.agentManager = agentManager;
        this.agentCommandSender = agentCommandSender;
    }

    @Override
    public void onStart() {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.REAPER_AGENT_MANAGER_KEY, agentManager);
        data.put(Constants.REAPER_AGENTS_HELPER_KEY, executableAgentsHelper);
        data.put(com.atlassian.buildeng.isolated.docker.reaper.Constants.REAPER_COMMAND_SENDER_KEY, agentCommandSender);
        pluginScheduler.scheduleJob(Constants.REAPER_KEY,ReaperJob.class, data, new Date(), Constants.REAPER_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(Constants.REAPER_KEY);
    }
}
