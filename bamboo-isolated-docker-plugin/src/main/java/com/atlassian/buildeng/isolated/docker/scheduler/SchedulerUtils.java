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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections.MapUtils;
import org.jetbrains.annotations.VisibleForTesting;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.utils.Key;
import org.slf4j.Logger;

public class SchedulerUtils {
    private final long JOB_AWAIT_INTERVAL = Duration.ofSeconds(5).toMillis();
    private final Scheduler scheduler;
    private final Logger logger;

    public SchedulerUtils(Scheduler scheduler, Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void awaitPreviousJobExecutions(List<JobKey> previousJobKeys) {
        try {
            boolean waiting = true;
            do {
                List<String> stillExecutingJobs = scheduler.getCurrentlyExecutingJobs().stream()
                        .map(JobExecutionContext::getJobDetail)
                        .map(JobDetail::getKey)
                        .filter(previousJobKeys::contains)
                        .map(Key::getName)
                        .collect(Collectors.toList());

                if (stillExecutingJobs.isEmpty()) {
                    logger.info("No currently running jobs from prior instance. Now scheduling new jobs.");
                    waiting = false;
                } else {
                    logger.info("Still waiting on job(s): " + String.join(", ", stillExecutingJobs));
                    Thread.sleep(getAwaitInterval());
                }
            } while (waiting);
        } catch (SchedulerException | InterruptedException e) {
            logger.warn("Was not able to determine if there is a currently running instance of scheduled jobs. "
                    + "Proceeding as if there are none. Exception thrown:\n" + e);
        }
    }

    /**
     * Copy over all data from previous job data maps. It's easiest to copy everything, then overwrite later.
     * If references to classes are included in this map, the new map WILL include references to classes
     * from the previous plugin instance's class loader, which MUST be overwritten to avoid {@link ClassCastException}
     *
     * <p>Jobs which need their data maps copied over MUST be store durably and unscheduled (not deleted)!
     *
     * @param config          The new {@link JobDataMap} which the old jobs data map should be merged into
     * @param previousJobKeys A list of {@link JobKey} which the job data maps should be extracted from
     **/
    public void copyPreviousJobDataAndDeleteJob(JobDataMap config, List<JobKey> previousJobKeys) {
        previousJobKeys.stream()
            .map(k -> {
                try {
                    return Optional.ofNullable(scheduler.getJobDetail(k));
                } catch (SchedulerException e) {
                    logger.warn("Unable to fetch previous job detail for job with key {}, will not merge its map.",
                            k.getName());
                    return Optional.<JobDetail>empty();
                }
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .forEach(jobDetail -> {
                logger.debug("Previous map: " + jobDetail.getKey() + "="
                        + MapUtils.toProperties(jobDetail.getJobDataMap()));
                config.putAll(jobDetail.getJobDataMap());
                JobKey key = jobDetail.getKey();
                try {
                    logger.info("Deleting old job for {}", key);
                    boolean deletion = scheduler.deleteJob(key);
                    if (!deletion) {
                        logger.warn("Was not able to find {} job. Was it already deleted?", key);
                    }
                } catch (SchedulerException e) {
                    logger.error("Was not able to delete {} job. Proceeding anyway. Exception thrown:\n{}", key, e);
                }
            });
    }

    @VisibleForTesting
    // We don't want to wait around in the tests, so we use a getter that can be mocked
    long getAwaitInterval() {
        return JOB_AWAIT_INTERVAL;
    }
}
