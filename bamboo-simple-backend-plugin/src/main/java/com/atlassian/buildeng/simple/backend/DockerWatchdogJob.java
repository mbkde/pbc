/*
 * Copyright 2016 Atlassian.
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

import com.atlassian.sal.api.scheduling.PluginJob;
import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author mkleint
 */
public class DockerWatchdogJob implements PluginJob {

    @Override
    public void execute(Map<String, Object> jobDataMap) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/usr/local/bin/docker", "ps", "-a", "--format",  "{{.ID}}::{{.Status}}::{{.Label \"com.docker.compose.service\"}}::{{.Label \"bamboo.uuid\"}}");
            Process p = pb.start();
            p.waitFor();
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                List<PsItem> all = buffer.lines()
                        .map((String t) -> {
                            List<String> splitted = Splitter.on("::").splitToList(t);
                            if (splitted.size() == 4) {
                                return new PsItem(splitted.get(0), splitted.get(2), splitted.get(1), splitted.get(3));
                            }
                            return null;
                        }).filter((PsItem t) -> t != null).collect(Collectors.toList());
                all.stream()
                        .filter((PsItem t) -> t.isExited() && t.name.equals("bamboo-agent"))
                        .map((PsItem t) -> t.uuid)
                        .forEach((String t) -> {
                    try {
                        ProcessBuilder rm = new ProcessBuilder("/usr/local/bin/docker-compose", "down", "-v");
                        rm.environment().put("COMPOSE_PROJECT_NAME", t);
                        rm.environment().put("COMPOSE_FILE", IsolatedDockerImpl.fileForUUID(t).getAbsolutePath());
                        Process p2 = rm.inheritIO().start();
                    } catch (IOException ex) {
                        Logger.getLogger(DockerWatchdogJob.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }        
        } catch (IOException ex) {
        } catch (InterruptedException ex) {
            Logger.getLogger(DockerWatchdogJob.class.getName()).log(Level.SEVERE, null, ex);
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
