package com.atlassian.buildeng.ecs.scheduling;

import com.atlassian.buildeng.ecs.exceptions.ECSException;

public interface ECSScheduler {
    /**
     * Return an ECS Container Instance ARN suitable to run the given resource requirements.
     * @param cluster The cluster to run the task on.
     * @param asgName AutoScaling Group to use
     * @param requiredMemory
     * @param requiredCpu
     * @return The ARN of container instances suitable to use. If nothing is suitable, null is returned;
     * @throws com.atlassian.buildeng.ecs.exceptions.ECSException
     */

    String schedule(String cluster, String asgName, int requiredMemory, int requiredCpu) throws ECSException;
}
