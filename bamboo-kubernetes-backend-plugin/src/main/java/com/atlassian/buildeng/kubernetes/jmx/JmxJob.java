/*
 * Copyright 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.jmx;

import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.WatchdogJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmxJob extends WatchdogJob  {
    private static final Logger logger = LoggerFactory.getLogger(JmxJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            BuildQueueManager buildQueueManager = getService(BuildQueueManager.class, "buildQueueManager");
            KubeJmxService jmxService = getService(KubeJmxService.class,
                    "kubeJmxService", context.getJobDetail().getJobDataMap());
            jmxService.recalculate(buildQueueManager);
        } catch (Throwable t) {
            // this is throwable because of NoClassDefFoundError and alike.
            // These are not Exception subclasses and actually
            // throwing something here will stop rescheduling the job forever (until next redeploy)
            logger.error("Exception caught and swallowed to preserve rescheduling of the task", t);
        }
    }
}
