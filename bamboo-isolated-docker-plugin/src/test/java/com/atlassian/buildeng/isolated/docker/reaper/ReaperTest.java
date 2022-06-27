/*
 * Copyright 2022 Atlassian Pty Ltd.
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.ExecutableAgentsHelper;
import com.atlassian.buildeng.isolated.docker.AgentRemovals;
import com.atlassian.buildeng.isolated.docker.UnmetRequirements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

public class ReaperTest {

    private Scheduler scheduler;
    private ExecutableAgentsHelper executableAgentsHelper;
    private AgentManager agentManager;
    private AgentRemovals agentRemovals;
    private UnmetRequirements unmetRequirements;

    private Reaper reaper;

    @BeforeEach
    public void setUp() {
        scheduler = mock(Scheduler.class);
        executableAgentsHelper = mock(ExecutableAgentsHelper.class);
        agentManager = mock(AgentManager.class);
        agentRemovals = mock(AgentRemovals.class);
        unmetRequirements = mock(UnmetRequirements.class);

        reaper = new Reaper(scheduler, executableAgentsHelper, agentManager, agentRemovals, unmetRequirements);
    }

    @Test
    public void deleteJobIsCalledOnStart() throws SchedulerException {
        // We only need to check this is called once as we are updating the code from unscheduling to deleting.
        // Once this version has been deployed we will no longer need to ensure the job was deleted
        // as it should be done onStop().
        reaper.onStart();

        verify(scheduler, times(1)).deleteJob(Reaper.REAPER_KEY);
    }

    @Test
    public void deleteJobIsCalledOnStop() throws SchedulerException {
        // The scheduler does not allow multiple jobs to run with the same key, and if
        // we don't delete it, the old job will never be unloaded
        reaper.onStop();

        verify(scheduler, times(1)).deleteJob(Reaper.REAPER_KEY);

    }

}
