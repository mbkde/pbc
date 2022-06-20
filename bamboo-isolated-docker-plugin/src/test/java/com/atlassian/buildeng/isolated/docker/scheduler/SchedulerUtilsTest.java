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

package com.atlassian.buildeng.isolated.docker.scheduler;


import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class SchedulerUtilsTest {
    @Mock
    private Scheduler scheduler;
    @Mock
    private Logger logger;

    @InjectMocks
    @Spy
    private SchedulerUtils schedulerUtils;

    @Test
    public void testWhenNoJobsAreStillRunningItDoesNotWait() throws SchedulerException {
        when(scheduler.getCurrentlyExecutingJobs()).thenReturn(Collections.emptyList());

        schedulerUtils.awaitPreviousJobExecutions(Collections.emptyList());

        verify(scheduler, times(1)).getCurrentlyExecutingJobs();
    }

    @Test
    public void testWhenOldJobsAreRunningItWaitsUntilTheyFinish() throws SchedulerException {
        Mockito.doReturn(1L).when(schedulerUtils).getAwaitInterval();

        JobExecutionContext jec = Mockito.mock(JobExecutionContext.class);
        JobDetail jd = Mockito.mock(JobDetail.class);
        JobKey jobKey = JobKey.jobKey("test");
        when(jec.getJobDetail()).thenReturn(jd);
        when(jd.getKey()).thenReturn(jobKey);

        AtomicInteger counter = new AtomicInteger(0);
        int expectedInvocations = 3;

        when(scheduler.getCurrentlyExecutingJobs()).then(r -> {
            if (counter.incrementAndGet() < expectedInvocations) {
                return Collections.singletonList(jec);
            } else {
                return Collections.emptyList();
            }
        });

        schedulerUtils.awaitPreviousJobExecutions(Collections.singletonList(jobKey));

        verify(scheduler, times(expectedInvocations)).getCurrentlyExecutingJobs();
    }

    @Test
    public void testJobDataMapIsCopied() throws SchedulerException {
        JobDetail jd = Mockito.mock(JobDetail.class);
        JobKey jobKey = JobKey.jobKey("test");
        when(jd.getKey()).thenReturn(jobKey);
        when(scheduler.getJobDetail(jobKey)).thenReturn(jd);

        JobDataMap previousData = new JobDataMap();
        previousData.put("previousKey", "previousValue");
        when(jd.getJobDataMap()).thenReturn(previousData);
        JobDataMap newData = new JobDataMap();

        when(scheduler.deleteJob(jobKey)).thenReturn(true);

        schedulerUtils.copyPreviousJobDataAndDeleteJob(newData, Collections.singletonList(jobKey));

        verify(scheduler, times(1)).deleteJob(jobKey);
        verify(scheduler, times(1)).getJobDetail(jobKey);
        Assertions.assertEquals(previousData, newData);
    }
}