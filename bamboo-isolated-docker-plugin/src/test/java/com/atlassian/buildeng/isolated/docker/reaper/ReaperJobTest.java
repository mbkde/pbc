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

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.quartz.JobBuilder.newJob;
import org.quartz.JobDetail;

public class ReaperJobTest {

    @Test
    public void jobDataShouldPersist() {
        JobDetail reaperJobDetail = newJob(ReaperJob.class)
                .build();

        assertTrue(reaperJobDetail.isPersistJobDataAfterExecution());
    }
}
