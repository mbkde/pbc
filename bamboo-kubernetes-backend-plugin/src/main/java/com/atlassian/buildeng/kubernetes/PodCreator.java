/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PodCreator {
    /**
     * The environment variable to override on the agent per image
     */
    static String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server
     */
    static String ENV_VAR_SERVER = "BAMBOO_SERVER";

    /**
     * The environment variable to set the result spawning up the agent
     */
    static String ENV_VAR_RESULT_ID = "RESULT_ID";

    // The working directory of isolated agents
    static final String WORK_DIR = "/buildeng";
    // The working directory for builds
    static final String BUILD_DIR = WORK_DIR + "/bamboo-agent-home/xml-data/build-dir";

    // Ratio between soft and hard limits
    static final Double SOFT_TO_HARD_LIMIT_RATIO = 1.25;
    
    static final String CONTAINER_NAME_BAMBOOAGENT = "bamboo-agent";


    static Map<String, Object> create(IsolatedDockerAgentRequest r, GlobalConfiguration globalConfiguration) {
        Configuration c = r.getConfiguration();
        Map<String, Object> root = new HashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Pod");
        root.put("metadata", createMetadata(globalConfiguration, r));
        root.put("spec", createSpec(globalConfiguration, r));
        return root;
    }

    private static Map<String, String> createAnnotations(GlobalConfiguration globalConfiguration) {
        Map<String, String> annotations = new HashMap<>();
        return annotations;
    }

    private static Map<String, String> createLabels(IsolatedDockerAgentRequest r) {
        Map<String, String> labels = new HashMap<>();
        labels.put("pbc", "true");
        labels.put("pbc.resultId", r.getResultKey());
        labels.put("pbc.uuid",  r.getUniqueIdentifier().toString());
        return labels;
    }

    public static boolean isDockerInDockerImage(String image) {
        return image.startsWith("docker:") && image.endsWith("dind");
    }

    private static List<Map<String, Object>> createExtraContainers(Configuration c) {
        return c.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", t.getName());
            map.put("image", t.getImage());
            map.put("imagePullPolicy", "Always");
            map.put("resources", createResources(t.getExtraSize().memory(), t.getExtraSize().cpu()));
            map.put("securityContext", ImmutableMap.of("privileged", isDockerInDockerImage(t.getImage())));
            map.put("command", t.getCommands());
            map.put("env", t.getEnvVariables().stream()
                        .map((Configuration.EnvVariable t1) -> ImmutableMap.of("name", t1.getName(), "value", t1.getValue()))
                        .collect(Collectors.toList()));
            map.put("volumeMounts", ImmutableMap.of("name", "workdir", "mountPath", BUILD_DIR, "readOnly", false));
            map.put("readinessProbe", createReadinessProbe(c, t.getName()));
            return map;
        }).collect(Collectors.toList());
    }

    private static List<String> containerNames(Configuration config) {
        return Stream.concat(Stream.of(CONTAINER_NAME_BAMBOOAGENT), config.getExtraContainers().stream().map(t -> t.getName()))
                .collect(Collectors.toList());
    }

    private static Map<String, Object> createReadinessProbe(Configuration c, String currentName) {
        Map<String, Object> map = new HashMap<>();
        map.put("periodSeconds", 5);
        map.put("initialDelaySeconds", 5);
        StringBuilder cmd = new StringBuilder();
        cmd.append("if [ -f /touch-").append(currentName).append(" ]; then { echo exists;} else {");
        containerNames(c).forEach(t -> {
            cmd.append(" echo '127.0.0.1 ").append(t).append("' >> /etc/hosts; ");
        });
        cmd.append(" touch /touch-").append(currentName).append("; } fi");
        map.put("exec", ImmutableMap.of("command", ImmutableList.of("/bin/sh", "-c", cmd.toString())));
        return map;
    }

    private static Map<String, Object> createMetadata(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", r.getResultKey().toLowerCase(Locale.ENGLISH) + "-" + r.getUniqueIdentifier().toString());
        map.put("labels", createLabels(r));
        map.put("annotations", createAnnotations(globalConfiguration));
        return map;
    }

    private static Object createSpec(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("restartPolicy", "Never");
        map.put("volumes", createVolumes());
        map.put("containers", createContainers(globalConfiguration, r));
        List<Map<String, Object>> initContainersList = new ArrayList<>();
        initContainersList.add(createSidekick(globalConfiguration.getCurrentSidekick()));
        map.put("initContainers", initContainersList);
        return map;
    }

    private static List<Map<String, Object>> createVolumes() {
        return ImmutableList.of(
            ImmutableMap.of("name", "workdir", "emptyDir", new HashMap<>()),
            ImmutableMap.of("name", "bamboo-agent-sidekick", "emptyDir", new HashMap<>()));
    }

    private static List<Map<String, Object>> createContainers(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        ArrayList<Map<String, Object>> toRet = new ArrayList<>();
        toRet.addAll(createExtraContainers(r.getConfiguration()));
        toRet.add(createMainContainer(globalConfiguration, r));
        return toRet;
    }

    private static Map<String, Object> createSidekick(String currentSidekick) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "bamboo-agent-sidekick");
        map.put("image", currentSidekick);
        map.put("imagePullPolicy", "Always");
        map.put("command", ImmutableList.of("sh", "-c", "cp -r /buildeng/* /buildeng-data"));
        map.put("volumeMounts", ImmutableList.of(ImmutableMap.of("name", "bamboo-agent-sidekick", "mountPath", "/buildeng-data", "readOnly", false)));
        return map;
    }

    private static Map<String, Object> createResources(int memory, int cpu) {
        return ImmutableMap.of(
                "limits", ImmutableMap.of("memory", "" + (long)(memory  * SOFT_TO_HARD_LIMIT_RATIO) + "Mi"),
                "requests", ImmutableMap.of("memory", "" + memory + "Mi", "cpu", "" + cpu + "m")
                );
    }

    private static Map<String, Object> createMainContainer(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", CONTAINER_NAME_BAMBOOAGENT);
        map.put("image", r.getConfiguration().getDockerImage());
        map.put("imagePullPolicy", "Always");
        map.put("workingDir", WORK_DIR);
        map.put("command", ImmutableList.of("sh", "-c", "/buildeng/run-agent.sh"));
        map.put("env", createMainContainerEnvs(globalConfiguration, r));
        map.put("volumeMounts", ImmutableList.of(
                ImmutableMap.of("name", "bamboo-agent-sidekick", "mountPath", WORK_DIR, "readOnly", false),
                ImmutableMap.of("name", "workdir", "mountPath", BUILD_DIR, "readOnly", false)
                ));
        map.put("resources", createResources(r.getConfiguration().getSize().memory(), r.getConfiguration().getSize().cpu()));
        map.put("readinessProbe", createReadinessProbe(r.getConfiguration(), CONTAINER_NAME_BAMBOOAGENT));
        return map;
    }

    private static Object createMainContainerEnvs(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        List<Map<String, Object>> envs = new ArrayList<>();
        Optional<Configuration.ExtraContainer> optDind = r.getConfiguration().getExtraContainers().stream().filter((Configuration.ExtraContainer t) -> isDockerInDockerImage(t.getImage())).findFirst();
        if (optDind.isPresent()) {
            envs.add(ImmutableMap.of("name", "DOCKER_HOST", "value", "tcp://" + optDind.get().getName() + ":2375"));
        }
        envs.add(ImmutableMap.of("name", ENV_VAR_IMAGE, "value", r.getConfiguration().getDockerImage()));
        envs.add(ImmutableMap.of("name", ENV_VAR_RESULT_ID, "value", r.getResultKey()));
        envs.add(ImmutableMap.of("name", ENV_VAR_SERVER, "value", globalConfiguration.getBambooBaseUrl()));
        envs.add(ImmutableMap.of("name", "QUEUE_TIMESTAMP", "value", "" + r.getQueueTimestamp()));
        envs.add(ImmutableMap.of("name", "SUBMIT_TIMESTAMP", "value", "" + System.currentTimeMillis()));
        return envs;
    }
}
