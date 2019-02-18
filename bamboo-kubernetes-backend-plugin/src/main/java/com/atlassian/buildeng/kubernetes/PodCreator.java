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

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

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

    public static final String ANN_RESULTID = "pbc.resultId";
    public static final String ANN_RETRYCOUNT = "pbc.retryCount";
    public static final String ANN_UUID = "pbc.uuid";
    public static final String LABEL_PBC_MARKER = "pbc";
    public static final String LABEL_BAMBOO_SERVER = "pbc.bamboo.server";
    
    /**
     * generate volume with memory fs at /dev/shm via https://docs.openshift.org/latest/dev_guide/shared_memory.html
     * the preferable solution is to modify the docker daemon's --default-shm-size parameter on hosts but it only
     * comes with docker 17.06+
     * 
     */
    private static final boolean GENERATE_SHM_VOLUME = Boolean.parseBoolean(
            System.getProperty("pbc.kube.shm.generate", "true"));
    

    static Map<String, Object> create(IsolatedDockerAgentRequest r, GlobalConfiguration globalConfiguration) {
        Configuration c = r.getConfiguration();
        Map<String, Object> root = new HashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Pod");
        root.put("metadata", createMetadata(globalConfiguration, r));
        root.put("spec", createSpec(globalConfiguration, r));
        return root;
    }

    private static Map<String, String> createAnnotations(IsolatedDockerAgentRequest r) {
        Map<String, String> annotations = new HashMap<>();
        annotations.put(ANN_UUID,  r.getUniqueIdentifier().toString());
        annotations.put(ANN_RESULTID, r.getResultKey());
        annotations.put(ANN_RETRYCOUNT, Integer.toString(r.getRetryCount()));
        return annotations;
    }

    private static Map<String, String> createLabels(IsolatedDockerAgentRequest r, GlobalConfiguration c) {
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_PBC_MARKER, "true");
        
        //TODO remove these two in the future, no need to have them as labels.
        labels.put(ANN_RESULTID, StringUtils.abbreviate(r.getResultKey(), 59) + "Z");
        labels.put(ANN_UUID,  r.getUniqueIdentifier().toString());
        
        labels.put(LABEL_BAMBOO_SERVER, c.getBambooBaseUrlAskKubeLabel());
        return labels;
    }
    

    public static boolean isDockerInDockerImage(String image) {
        return image.contains("docker:") && image.endsWith("dind");
    }

    private static List<Map<String, Object>> createExtraContainers(Configuration c,
            GlobalConfiguration globalConfiguration) {
        return c.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", t.getName());
            map.put("image", sanitizeImageName(t.getImage()));
            map.put("imagePullPolicy", "Always");
            ContainerSizeDescriptor sizeDescriptor = globalConfiguration.getSizeDescriptor();
            map.put("resources", createResources(sizeDescriptor.getMemory(t.getExtraSize()),
                    sizeDescriptor.getMemoryLimit(t.getExtraSize()),
                    sizeDescriptor.getCpu(t.getExtraSize())));
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
            ImmutableList.Builder<Map<String, Object>> mountsBuilder = 
                    ImmutableList.<Map<String, Object>>builder().addAll(commonVolumeMounts());
            if (GENERATE_SHM_VOLUME) {
                mountsBuilder.add(ImmutableMap.of("name", "shm", "mountPath", "/dev/shm", "readOnly", false));
            }
            map.put("volumeMounts", mountsBuilder.build());
            
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
        if (StringUtils.isNoneBlank(Constants.DIND_EXTRA_ARGS)) {
            //do we need to split on space
            String[] split = StringUtils.split(Constants.DIND_EXTRA_ARGS, " ");
            cmds.addAll(Arrays.asList(split));
        }
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
        map.put("annotations", createAnnotations(r));
        return map;
    }

    static String createPodName(IsolatedDockerAgentRequest r) {
        //50 is magic constant that attempts to limit the overall length of the name.
        String key = r.getResultKey();
        if (key.length() > 50) {
            PlanResultKey prk = PlanKeys.getPlanResultKey(key);
            String pk = prk.getPlanKey().toString();
            int len = 50 - ("" + prk.getBuildNumber()).length() - 1;
            key = pk.substring(0, 
                    Math.min(pk.length(), len)) + "-" + prk.getBuildNumber();
        }
        //together with the identifier we are not at 88 max,in BUILDENG-15619 the failure in CNI plugin network creation
        // was linked both to pod name and namespace lengths. this way we should accomodate fairly long namespace names.
        return key.toLowerCase(Locale.ENGLISH) + "-" + r.getUniqueIdentifier().toString();
    }

    private static Object createSpec(GlobalConfiguration globalConfiguration, IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("restartPolicy", "Never");
        //63 is max - https://tools.ietf.org/html/rfc2181#section-11
        String hostname = StringUtils.left(r.getResultKey().toLowerCase(Locale.ENGLISH), 63);
        if (hostname.length() == 63 && hostname.charAt(62) == '-') {
            hostname = hostname.substring(0, 62);
        }
        map.put("hostname", hostname);
        map.put("volumes", createVolumes());
        map.put("containers", createContainers(globalConfiguration, r));
        List<Map<String, Object>> initContainersList = new ArrayList<>();
        initContainersList.add(createSidekick(globalConfiguration.getCurrentSidekick()));
        map.put("initContainers", initContainersList);
        map.put("hostAliases", createLocalhostAliases(r.getConfiguration()));
        return map;
    }

    private static List<Map<String, Object>> createVolumes() {
        ImmutableList.Builder<Map<String, Object>> bldr = ImmutableList.builder();
        if (GENERATE_SHM_VOLUME) {
            // workaround for low default of 64M in docker daemon. 
            //https://docs.openshift.org/latest/dev_guide/shared_memory.html
            // since docker daemon 17.06 
            //the default size should be configurable on the docker daemon size and is likely preferable.
            bldr.add(ImmutableMap.of("name", "shm", "emptyDir", ImmutableMap.of("medium", "Memory")));
        }
        bldr.add(ImmutableMap.of("name", "workdir", "emptyDir", new HashMap<>()))
            .add(ImmutableMap.of("name", "pbcwork", "emptyDir", new HashMap<>()))
            .add(ImmutableMap.of("name", "bamboo-agent-sidekick", "emptyDir", new HashMap<>()));
        return bldr.build();
    }

    private static List<Map<String, Object>> createContainers(GlobalConfiguration globalConfiguration,
            IsolatedDockerAgentRequest r) {
        ArrayList<Map<String, Object>> toRet = new ArrayList<>();
        toRet.addAll(createExtraContainers(r.getConfiguration(), globalConfiguration));
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
        map.put("resources", createResources(SIDEKICK_MEMORY, SIDEKICK_MEMORY, SIDEKICK_CPU));
        return map;
    }

    private static Map<String, Object> createResources(int softMemory, int hardMemory, int cpu) {
        return ImmutableMap.of(
                "limits", ImmutableMap.of("memory", "" + hardMemory + "Mi"),
                "requests", ImmutableMap.of("memory", "" + softMemory + "Mi", "cpu", "" + cpu + "m")
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
        ImmutableList.Builder<Map<String, Object>> mountsBuilder = ImmutableList.<Map<String, Object>>builder()
                .add(ImmutableMap.of("name", "bamboo-agent-sidekick", "mountPath", WORK_DIR, "readOnly", false))
                .addAll(commonVolumeMounts());
        if (GENERATE_SHM_VOLUME) {
            mountsBuilder.add(ImmutableMap.of("name", "shm", "mountPath", "/dev/shm", "readOnly", false));
        }
        map.put("volumeMounts", mountsBuilder.build());
        ContainerSizeDescriptor sizeDescriptor = globalConfiguration.getSizeDescriptor();
        map.put("resources", createResources(
                sizeDescriptor.getMemory(r.getConfiguration().getSize()),
                sizeDescriptor.getMemoryLimit(r.getConfiguration().getSize()),
                sizeDescriptor.getCpu(r.getConfiguration().getSize())));
        return map;
    }
    
    private static List<Map<String, Object>> commonVolumeMounts() {
        return ImmutableList.of(
                ImmutableMap.of("name", "workdir", "mountPath", BUILD_DIR, "readOnly", false),
                ImmutableMap.of("name", "pbcwork", "mountPath", "/pbc", "readOnly", false)
        );
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
