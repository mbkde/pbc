package com.atlassian.buildeng.ecs.scheduling;

public interface ECSScheduler {

    /* Run the given resource requirements on ECS.
     */
    void schedule(SchedulingRequest request, SchedulingCallback callback);
}
