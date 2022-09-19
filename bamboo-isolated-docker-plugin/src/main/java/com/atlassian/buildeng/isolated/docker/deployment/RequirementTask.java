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

package com.atlassian.buildeng.isolated.docker.deployment;

import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.task.CommonTaskContext;
import com.atlassian.bamboo.task.CommonTaskType;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.agent.ExecutableBuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.AgentContext;
import java.util.Map;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;

public class RequirementTask implements CommonTaskType {

    private final AgentContext agentContext;

    @Inject
    public RequirementTask(AgentContext agentContext) {
        this.agentContext = agentContext;
    }
    
    @Override
    public TaskResult execute(@NotNull CommonTaskContext commonTaskContext) throws TaskException {
        CommonContext cc = commonTaskContext.getCommonContext();
        if (cc instanceof BuildContext) {
            //task ignored in jobs
            commonTaskContext.getBuildLogger()
                .addErrorLogEntry("Task 'Per-build Container (PBC) Agent for Deployments' "
                        + "is only relevant for deployments, ignored in jobs.");
        } else if (cc instanceof DeploymentContext) {
            DeploymentContext dc = (DeploymentContext)cc;
            commonTaskContext.getBuildLogger()
                .addBuildLogEntry("Task 'Per-build Container (PBC) Agent for Deployments' "
                    + "is deprecated, please use environment's Docker tab configuration.");
            Map<String, String> map = dc.getPluginConfigMap("com.atlassian.buildeng.bamboo-isolated-docker-plugin");
            if (!map.isEmpty()) {
                commonTaskContext.getBuildLogger()
                    .addErrorLogEntry("Task 'Per-build Container (PBC) Agent for Deployments' "
                        + "configuration is not used, using config from environment's Docker tab.");
                
            }
        }
        
        //TODO when the task doesn't run anymore, do we get deployment agents running forever??
        ExecutableBuildAgent agent = agentContext.getBuildAgent();
        if (agent != null) {
            agent.stopNicely();
        }
        return TaskResultBuilder.newBuilder(commonTaskContext).build();
    }
}
