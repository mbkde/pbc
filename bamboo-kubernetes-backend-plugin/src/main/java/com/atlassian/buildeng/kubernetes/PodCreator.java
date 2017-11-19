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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;

public class PodCreator {
    /**
     * The environment variable to override on the agent per image.
     */
    static final String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server.
     */
    static final String ENV_VAR_SERVER = "BAMBOO_SERVER";

    /**
     * The environment variable to set the result spawning up the agent.
     */
    static final String ENV_VAR_RESULT_ID = "RESULT_ID";
    
    /**
     * The environment variable with the number of side containers in the pod.
     * Used by the sidekick run_agent script to wait until all containers started up.
     */
    static final String KUBE_NUM_EXTRA_CONTAINERS = "KUBE_NUM_EXTRA_CONTAINERS";
    

    /**
     * Sidekick container limits. Specific to Kubernetes as we have to run the container and copy files
     * into the pod volume, something not necessary for ECS as they use regular Docker volumes.
     */
    static final int SIDEKICK_MEMORY = 500;
    static final int SIDEKICK_CPU  = 500;
    
    // The working directory of isolated agents
    static final String WORK_DIR = "/buildeng";
    // The working directory for builds
    static final String BUILD_DIR = WORK_DIR + "/bamboo-agent-home/xml-data/build-dir";
    
    /**
     * The directory that all containers write an empty file to during postStart hook
     * the purpose is to have the agent container wait until all side containers have written to this directory.
     */
    static final String PBC_DIR = "/pbc/kube";

    static final String CONTAINER_NAME_BAMBOOAGENT = "bamboo-agent";

    public static final String LABEL_PBC_MARKER = "pbc";
    public static final String LABEL_RESULTID = "pbc.resultId";
    public static final String LABEL_UUID = "pbc.uuid";
    public static final String LABEL_BAMBOO_SERVER = "pbc.bamboo.server";
    

    static Map<String, Object> create(IsolatedDockerAgentRequest r, GlobalConfiguration globalConfiguration) {
        Configuration c = r.getConfiguration();
        Map<String, Object> root = new HashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Pod");
        root.put("metadata", createMetadata(globalConfiguration, r));
        root.put("spec", createSpec(globalConfiguration, r));
        return root;
    }

    private static Map<String, String> createAnnotations() {
        return new HashMap<>();
    }

    private static Map<String, String> createLabels(IsolatedDockerAgentRequest r, GlobalConfiguration c) {
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_PBC_MARKER, "true");
        labels.put(LABEL_RESULTID, r.getResultKey());
        labels.put(LABEL_UUID,  r.getUniqueIdentifier().toString());
        labels.put(LABEL_BAMBOO_SERVER, c.getBambooBaseUrlAskKubeLabel());
        return labels;
    }
    

    public static boolean isDockerInDockerImage(String image) {
        return image.startsWith("docker:") && image.endsWith("dind");
    }

    private static List<Map<String, Object>> createExtraContainers(Configuration c) {
        return c.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", t.getName());
            map.put("image", sanitizeImageName(t.getImage()));
            map.put("imagePullPolicy", "Always");
            map.put("resources", createResources(t.getExtraSize().memory(), t.getExtraSize().cpu()));
            if (isDockerInDockerImage(t.getImage())) {
                map.put("securityContext", ImmutableMap.of("privileged", Boolean.TRUE));
                map.put("args", adjustCommandsForDind(t.getCommands()));
            } else {
                map.put("args", t.getCommands());
            }
            map.put("env", t.getEnvVariables().stream()
                        .map((Configuration.EnvVariable t1) ->
                                ImmutableMap.of("name", t1.getName(), "value", t1.getValue()))
                        .collect(Collectors.toList()));
            map.put("volumeMounts", ImmutableList.of(
                    ImmutableMap.of("name", "workdir", "mountPath", BUILD_DIR, "readOnly", false),
                    ImmutableMap.of("name", "pbcwork", "mountPath", "/pbc", "readOnly", false)
                    ));
            map.put("lifecycle", createContainerLifecycle(t.getName()));
            return map;
        }).collect(Collectors.toList());
    }
    
    /**
     * adjust the list of commands if required, eg. in case of storage-driver switch for
     * docker in docker images.
     */
    static List<String> adjustCommandsForDind(List<String> commands) {
        List<String> cmds = new ArrayList<>(commands);
        Iterator<String> it = cmds.iterator();
        while (it.hasNext()) {
            String s = it.next().trim();
            if (s.startsWith("-s") || s.startsWith("--storage-driver") || s.startsWith("--storage-opt")) {
                it.remove();
                if (!s.contains("=") && it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
        cmds.add("--storage-driver=" + Constants.STORAGE_DRIVER);
        return cmds;
    }
    

    static List<String> containerNames(Configuration config) {
        return Stream.concat(Stream.of(CONTAINER_NAME_BAMBOOAGENT), config.getExtraContainers().stream()
                .map(t -> t.getName()))
                .collect(Collectors.toList());
    }
    
    private static Map<String, Object> createMetadata(GlobalConfiguration globalConfiguration,
            IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", createPodName(r));
        map.put("labels", createLabels(r, globalConfiguration));
        map.put("annotations", createAnnotations());
        return map;
    }

    private static String createPodName(IsolatedDockerAgentRequest r) {
        return r.getResultKey().toLowerCase(Locale.ENGLISH) + "-" + r.getUniqueIdentifier().toString();
    }

    private static Object createSpec(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("restartPolicy", "Never");
        map.put("volumes", createVolumes());
        map.put("containers", createContainers(globalConfiguration, r));
        List<Map<String, Object>> initContainersList = new ArrayList<>();
        initContainersList.add(createSidekick(globalConfiguration.getCurrentSidekick()));
        map.put("initContainers", initContainersList);
        map.put("hostAliases", createLocalhostAliases(r.getConfiguration()));
        return map;
    }

    private static List<Map<String, Object>> createVolumes() {
        return ImmutableList.of(
            ImmutableMap.of("name", "workdir", "emptyDir", new HashMap<>()),
            ImmutableMap.of("name", "pbcwork", "emptyDir", new HashMap<>()),
            ImmutableMap.of("name", "bamboo-agent-sidekick", "emptyDir", new HashMap<>()));
    }

    private static List<Map<String, Object>> createContainers(GlobalConfiguration globalConfiguration,
            IsolatedDockerAgentRequest r) {
        ArrayList<Map<String, Object>> toRet = new ArrayList<>();
        toRet.addAll(createExtraContainers(r.getConfiguration()));
        toRet.add(createMainContainer(globalConfiguration, r));
        return toRet;
    }

    private static Map<String, Object> createSidekick(String currentSidekick) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "bamboo-agent-sidekick");
        map.put("image", sanitizeImageName(currentSidekick));
        map.put("imagePullPolicy", "Always");
        map.put("command", ImmutableList.of("sh", "-c", 
                          "cp -r /buildeng/* /buildeng-data;"
                        + "mkdir " + PBC_DIR + ";"
                        + "chmod a+wt " + PBC_DIR + ";"));
        map.put("volumeMounts", ImmutableList.of(
                ImmutableMap.of("name", "bamboo-agent-sidekick", "mountPath", "/buildeng-data", "readOnly", false),
                ImmutableMap.of("name", "pbcwork", "mountPath", "/pbc", "readOnly", false)));
        map.put("resources", createResources(SIDEKICK_MEMORY, SIDEKICK_CPU));
        return map;
    }

    private static Map<String, Object> createResources(int memory, int cpu) {
        return ImmutableMap.of(
                "limits", ImmutableMap.of("memory", "" + (long)(memory  * Constants.SOFT_TO_HARD_LIMIT_RATIO) + "Mi"),
                "requests", ImmutableMap.of("memory", "" + memory + "Mi", "cpu", "" + cpu + "m")
                );
    }

    private static Map<String, Object> createMainContainer(GlobalConfiguration globalConfiguration,
            IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", CONTAINER_NAME_BAMBOOAGENT);
        map.put("image", sanitizeImageName(r.getConfiguration().getDockerImage()));
        map.put("imagePullPolicy", "Always");
        map.put("workingDir", WORK_DIR);
        map.put("command", ImmutableList.of("sh", "-c", "/buildeng/run-agent.sh"));
        map.put("env", createMainContainerEnvs(globalConfiguration, r));
        map.put("volumeMounts", ImmutableList.of(
                ImmutableMap.of("name", "bamboo-agent-sidekick", "mountPath", WORK_DIR, "readOnly", false),
                ImmutableMap.of("name", "workdir", "mountPath", BUILD_DIR, "readOnly", false),
                ImmutableMap.of("name", "pbcwork", "mountPath", "/pbc", "readOnly", false)
                ));
        map.put("resources", createResources(r.getConfiguration().getSize().memory(),
                r.getConfiguration().getSize().cpu()));
        return map;
    }

    private static Object createMainContainerEnvs(GlobalConfiguration globalConfiguration,
            IsolatedDockerAgentRequest r) {
        List<Map<String, Object>> envs = new ArrayList<>();
        Optional<Configuration.ExtraContainer> optDind = r.getConfiguration().getExtraContainers().stream()
                .filter((Configuration.ExtraContainer t) -> isDockerInDockerImage(t.getImage()))
                .findFirst();
        if (optDind.isPresent()) {
            envs.add(ImmutableMap.of("name", "DOCKER_HOST", "value", "tcp://" + optDind.get().getName() + ":2375"));
        }
        envs.add(ImmutableMap.of("name", ENV_VAR_IMAGE, "value", r.getConfiguration().getDockerImage()));
        envs.add(ImmutableMap.of("name", ENV_VAR_RESULT_ID, "value", r.getResultKey()));
        envs.add(ImmutableMap.of("name", ENV_VAR_SERVER, "value", globalConfiguration.getBambooBaseUrl()));
        envs.add(ImmutableMap.of("name", "QUEUE_TIMESTAMP", "value", "" + r.getQueueTimestamp()));
        envs.add(ImmutableMap.of("name", "SUBMIT_TIMESTAMP", "value", "" + System.currentTimeMillis()));
        envs.add(ImmutableMap.of("name", "KUBE_POD_NAME", "value", createPodName(r)));
        envs.add(ImmutableMap.of("name", KUBE_NUM_EXTRA_CONTAINERS, "value",
                "" + r.getConfiguration().getExtraContainers().size()));
        return envs;
    }

    private static Object createLocalhostAliases(Configuration c) {
        List<Map<String, Object>> ips = new ArrayList<>();
        ips.add(ImmutableMap.of(
                "ip", "127.0.0.1", 
                "hostnames", containerNames(c)));
        return ips;
    }
    
    /**
     * Add a postStart lifecycle hook that logs that the container has started.
     * The final user of the outcome is the agent container startup script that
     * needs to wait for all side containers to start up, avoiding the case when the build actually starts/finishes
     * but side containers haven't even started yet.
     */
    private static Map<String, Object> createContainerLifecycle(String containerName) {
        Map<String, Object> map = new HashMap<>();
        StringBuilder cmd = new StringBuilder();
        cmd.append("touch ").append(PBC_DIR + "/" + containerName);
        map.put("exec", ImmutableMap.of("command", ImmutableList.of("/bin/sh", "-c", cmd.toString())));
        return Collections.singletonMap("postStart", map);
    }  
    
    public static String sanitizeImageName(String image) {
        return image.trim();
    }
    
}
