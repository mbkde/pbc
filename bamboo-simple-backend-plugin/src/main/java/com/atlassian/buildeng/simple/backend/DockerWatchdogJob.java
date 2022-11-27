/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.simple.backend;

import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class DockerWatchdogJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(DockerWatchdogJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            executeImpl(context.getJobDetail().getJobDataMap());
        } catch (Throwable t) {
            // this is throwable because of NoClassDefFoundError and alike.
            // These are not Exception subclasses and actually
            // thowing something here will stop rescheduling the job forever (until next redeploy)
            logger.error("Exception catched and swallowed to preserve rescheduling of the task", t);
        }
    }

    public void executeImpl(Map<String, Object> jobDataMap) {
        GlobalConfiguration globalConfiguration = (GlobalConfiguration) jobDataMap.get("globalConfiguration");
        if (globalConfiguration == null) {
            throw new IllegalStateException();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(ExecutablePathUtils.getDockerBinaryPath(),
                    "ps",
                    "-a",
                    "--format",
                    "{{.ID}}::{{.Status}}::{{.Label \"com.docker.compose.service\"}}::{{.Label \"bamboo.uuid\"}}");
            globalConfiguration.decorateCommands(pb);
            Process p = pb.start();
            p.waitFor();
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                List<PsItem> all = buffer.lines().map((String t) -> {
                    List<String> splitted = Splitter.on("::").splitToList(t);
                    if (splitted.size() == 4) {
                        return new PsItem(splitted.get(0), splitted.get(2), splitted.get(1), splitted.get(3));
                    }
                    return null;
                }).filter((PsItem t) -> t != null).collect(Collectors.toList());
                all
                        .stream()
                        .filter((PsItem t) -> t.isExited() && t.name.equals("bamboo-agent"))
                        .map((PsItem t) -> t.uuid)
                        .forEach((String t) -> {
                            try {
                                ProcessBuilder rm = new ProcessBuilder(ExecutablePathUtils.getDockerComposeBinaryPath(),
                                        "down",
                                        "-v");
                                // yes. docker-compose up can pass -p and -f parameters but all other commands
                                // rely on env variables to do the same (facepalm)
                                globalConfiguration.decorateCommands(pb);
                                rm.environment().put("COMPOSE_PROJECT_NAME", t);
                                rm
                                        .environment()
                                        .put("COMPOSE_FILE", IsolatedDockerImpl.fileForUUID(t).getAbsolutePath());
                                Process p2 = rm.inheritIO().start();
                            } catch (IOException ex) {
                                logger.error("Failed to run docker-compose down", ex);
                            }
                        });
            }
        } catch (IOException ex) {
            logger.error("Failed to run docker commands", ex);
        } catch (InterruptedException ex) {
            logger.error("interrupted", ex);
        }
    }

    private class PsItem {
        final String id;
        final String name;
        final String status;
        final String uuid;

        public PsItem(String id, String name, String status, String uuid) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.uuid = uuid;
        }

        boolean isExited() {
            return status.contains("Exited");
        }
    }
}
