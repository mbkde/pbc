package com.atlassian.buildeng.ecs.scheduling;

public interface ECSScheduler {
    /**
     * Return an ECS Container Instance ARN suitable to run the given resource requirements.
     * @param cluster The cluster to run the task on.
     * @return The ARN of container instances suitable to use. If nothing is suitable, null is returned;
     */

    String schedule(String cluster, Integer requiredMemory, Integer requiredCpu);
}
