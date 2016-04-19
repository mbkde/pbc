package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ecs.model.StartTaskResult;
import com.atlassian.buildeng.ecs.exceptions.ECSException;

public interface ECSScheduler {

    /* Run the given resource requirements on ECS.
     * @throws com.atlassian.buildeng.ecs.exceptions.ECSException if nothing is suitable to run on
     */

    SchedulingResult schedule(SchedulingRequest request) throws ECSException;
}
