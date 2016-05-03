package com.atlassian.buildeng.isolated.docker.deployment;

import com.atlassian.bamboo.task.CommonTaskContext;
import com.atlassian.bamboo.task.CommonTaskType;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.v2.build.agent.ExecutableBuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.AgentContext;
import org.jetbrains.annotations.NotNull;

public class RequirementTask implements CommonTaskType
{

    private final AgentContext agentContext;

    public RequirementTask(AgentContext agentContext) {
        this.agentContext = agentContext;
    }
    
    @Override
    public TaskResult execute(@NotNull CommonTaskContext commonTaskContext) throws TaskException
    {
        ExecutableBuildAgent agent = agentContext.getBuildAgent();
        if (agent != null) {
            agent.stopNicely();
        }
        return TaskResultBuilder.newBuilder(commonTaskContext).build();
    }
}
