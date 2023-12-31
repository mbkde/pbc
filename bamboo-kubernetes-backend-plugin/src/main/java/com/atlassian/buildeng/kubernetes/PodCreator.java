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
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import com.atlassian.sal.api.features.DarkFeatureManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;

@BambooComponent
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
     * The environment variable to set the aws role.
     */
    static final String ENV_AWS_ROLE_ARN = "AWS_ROLE_ARN";

    /**
     * The environment variable to set the location of the web identity token for IRSA.
     */
    static final String ENV_AWS_WEB_IDENTITY = "AWS_WEB_IDENTITY_TOKEN_FILE";

    /**
     * The argument to set the security token for the Bamboo instance for which the agent is spawned.
     */
    static final String ARG_SECURITY_TOKEN = "SECURITY_TOKEN";
    /**
     * The environment variable with the number of side containers in the pod.
     * Used by the sidekick run_agent script to wait until all containers started up.
     */
    static final String KUBE_NUM_EXTRA_CONTAINERS = "KUBE_NUM_EXTRA_CONTAINERS";

    /**
     * Extra container CPU limit. This needs to be injected at run time and cannot be specified in the top level pod
     * template as it needs to be specified in the container section, which cannot be known until runtime, as users
     * can choose an arbitrary container name.
     */
    static final int EXTRA_CONTAINER_CPU_LIMIT = 20480;

    /**
     * Sidekick container limits. Specific to Kubernetes as we have to run the container and copy files
     * into the pod volume, something not necessary for ECS as they use regular Docker volumes.
     */
    static final int SIDEKICK_MEMORY = 500;

    static final int SIDEKICK_CPU = 500;

    // The working directory of isolated agents
    static final String WORK_DIR = "/buildeng";
    // The working directory for builds
    static final String BUILD_DIR = WORK_DIR + "/bamboo-agent-home/xml-data/build-dir";
    // The log spool folder which the Bamboo agent stores the build logs in
    static final String LOG_SPOOL_DIR = WORK_DIR + "/bamboo-agent-home/temp/log_spool";

    /**
     * The directory that all containers write an empty file to during postStart hook
     * the purpose is to have the agent container wait until all side containers have written to this directory.
     */
    static final String PBC_DIR = "/pbc/kube";

    static final String AWS_WEB_IDENTITY_TOKEN_FILE = "/var/run/secrets/eks.amazonaws.com/serviceaccount/";

    static final String CONTAINER_NAME_BAMBOOAGENT = "bamboo-agent";

    public static final String ANN_RESULTID = "pbc.resultId";
    public static final String ANN_RETRYCOUNT = "pbc.retryCount";
    public static final String ANN_UUID = "pbc.uuid";
    public static final String ANN_IAM_REQUEST_NAME = "pbc.iamRequestName";
    public static final String ANN_POD_NAME = "pbc.podName";

    public static final String LABEL_PBC_MARKER = "pbc";
    public static final String LABEL_BAMBOO_SERVER = "pbc.bamboo.server";

    static final Integer KUBE_NAME_MAX_LENGTH = 87;
    static final Integer IRSA_SECRET_MAX_LENGTH = 63;

    // very short suffix as we have a much stricter limit for labels
    static final String IRSA_SECRET_NAME_SUFFIX = "it";
    static final String IAM_REQUEST_NAME_SUFFIX = "iamrequest";

    private final GlobalConfiguration globalConfiguration;
    private final DarkFeatureManager darkFeatureManager;

    @Inject
    public PodCreator(GlobalConfiguration globalConfiguration, DarkFeatureManager darkFeatureManager) {
        this.globalConfiguration = globalConfiguration;
        this.darkFeatureManager = darkFeatureManager;
    }

    /**
     * generate volume with memory fs at /dev/shm via https://docs.openshift.org/latest/dev_guide/shared_memory.html
     * the preferable solution is to modify the docker daemon's --default-shm-size parameter on hosts but it only
     * comes with docker 17.06+
     */
    private static final boolean GENERATE_SHM_VOLUME =
            Boolean.parseBoolean(System.getProperty("pbc.kube.shm.generate", "true"));

    private static final String IMAGE_PULL_POLICY =
            new SystemProperty(false, "atlassian.bamboo.pbc.image.pull.policy").getValue("Always");

    Map<String, Object> create(IsolatedDockerAgentRequest r) {
        Map<String, Object> root = new HashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Pod");
        root.put("metadata", createMetadata(r));
        root.put("spec", createSpec(r));
        return root;
    }

    Map<String, Object> createIamRequest(IsolatedDockerAgentRequest r, String subjectId) {
        Map<String, Object> iamRequest = new HashMap<>();
        iamRequest.put("kind", "IAMRequest");
        iamRequest.put(
                "metadata",
                ImmutableMap.of(
                        "name",
                        createIamRequestName(r),
                        "annotations",
                        ImmutableMap.of(ANN_POD_NAME, createPodName(r))));
        iamRequest.put("spec", ImmutableMap.of("subjectID", subjectId, "outputSecretName", createIrsaSecretName(r)));
        return iamRequest;
    }

    private Map<String, String> createAnnotations(IsolatedDockerAgentRequest r) {
        Map<String, String> annotations = new HashMap<>();
        annotations.put(ANN_UUID, r.getUniqueIdentifier().toString());
        annotations.put(ANN_RESULTID, r.getResultKey());
        annotations.put(ANN_RETRYCOUNT, Integer.toString(r.getRetryCount()));

        if (r.getConfiguration().isAwsRoleDefined()) {
            annotations.put(ANN_IAM_REQUEST_NAME, createIamRequestName(r));
        }
        return annotations;
    }

    private Map<String, String> createLabels(IsolatedDockerAgentRequest r) {
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_PBC_MARKER, "true");

        // TODO remove these two in the future, no need to have them as labels.
        labels.put(ANN_RESULTID, StringUtils.abbreviate(r.getResultKey(), 59) + "Z");
        labels.put(ANN_UUID, r.getUniqueIdentifier().toString());
        labels.put(LABEL_BAMBOO_SERVER, globalConfiguration.getBambooBaseUrlAskKubeLabel());
        return labels;
    }

    public static boolean isDockerInDockerImage(String image) {
        return image.contains("docker:") && image.endsWith("dind");
    }

    private List<Map<String, Object>> createExtraContainers(Configuration c) {
        return c.getExtraContainers().stream()
                .map((Configuration.ExtraContainer t) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", t.getName());
                    map.put("image", sanitizeImageName(t.getImage()));
                    map.put("imagePullPolicy", IMAGE_PULL_POLICY);
                    ContainerSizeDescriptor sizeDescriptor = globalConfiguration.getSizeDescriptor();
                    map.put(
                            "resources",
                            createResources(
                                    sizeDescriptor.getMemory(t.getExtraSize()),
                                    sizeDescriptor.getMemoryLimit(t.getExtraSize()),
                                    sizeDescriptor.getCpu(t.getExtraSize()),
                                    EXTRA_CONTAINER_CPU_LIMIT));
                    if (isDockerInDockerImage(t.getImage())) {
                        map.put("securityContext", ImmutableMap.of("privileged", Boolean.TRUE));
                        map.put("args", adjustCommandsForDind(t.getCommands()));
                        List<Configuration.EnvVariable> currentEnvVariable = new LinkedList<>(t.getEnvVariables());
                        currentEnvVariable.add(new Configuration.EnvVariable("DOCKER_TLS_CERTDIR", ""));
                        t.setEnvVariables(currentEnvVariable);
                    } else {
                        map.put("args", t.getCommands());
                    }

                    ImmutableList.Builder<Map<String, Object>> mountsBuilder =
                            ImmutableList.<Map<String, Object>>builder().addAll(commonVolumeMounts());

                    if (c.isAwsRoleDefined()) {
                        mountsBuilder.add(ImmutableMap.of(
                                "name", "aws-iam-token", "mountPath", AWS_WEB_IDENTITY_TOKEN_FILE, "readOnly", true));
                        List<Configuration.EnvVariable> currentEnvVariable = new LinkedList<>(t.getEnvVariables());
                        currentEnvVariable.add(new Configuration.EnvVariable(ENV_AWS_ROLE_ARN, c.getAwsRole()));
                        currentEnvVariable.add(new Configuration.EnvVariable(
                                ENV_AWS_WEB_IDENTITY, AWS_WEB_IDENTITY_TOKEN_FILE + "token"));
                        t.setEnvVariables(currentEnvVariable);
                    }

                    // We've run into some edge case where env vars can be duplicated, see BUILDENG-20649. Use
                    // .distinct()
                    map.put(
                            "env",
                            t.getEnvVariables().stream()
                                    .distinct()
                                    .map((Configuration.EnvVariable t1) ->
                                            ImmutableMap.of("name", t1.getName(), "value", t1.getValue()))
                                    .collect(Collectors.toList()));

                    if (GENERATE_SHM_VOLUME) {
                        mountsBuilder.add(ImmutableMap.of("name", "shm", "mountPath", "/dev/shm", "readOnly", false));
                    }

                    map.put("volumeMounts", mountsBuilder.build());

                    map.put("lifecycle", createContainerLifecycle(t.getName()));
                    return map;
                })
                .collect(Collectors.toList());
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
            // do we need to split on space
            String[] split = StringUtils.split(Constants.DIND_EXTRA_ARGS, " ");
            cmds.addAll(Arrays.asList(split));
        }
        return cmds;
    }

    static List<String> containerNames(Configuration config) {
        return Stream.concat(
                        Stream.of(CONTAINER_NAME_BAMBOOAGENT),
                        config.getExtraContainers().stream().map(Configuration.ExtraContainer::getName))
                .collect(Collectors.toList());
    }

    private Map<String, Object> createMetadata(IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", createPodName(r));
        map.put("labels", createLabels(r));
        map.put("annotations", createAnnotations(r));
        return map;
    }

    String createPodName(IsolatedDockerAgentRequest r) {
        return createName(r, "", KUBE_NAME_MAX_LENGTH);
    }

    String createIrsaSecretName(IsolatedDockerAgentRequest r) {
        return "iamtoken-" + r.getUniqueIdentifier().toString();
    }

    String createIamRequestName(IsolatedDockerAgentRequest r) {
        return createName(r, IAM_REQUEST_NAME_SUFFIX, KUBE_NAME_MAX_LENGTH);
    }

    /**
     * A character for some resources are necessary. This functions reduces the name of a plan to 50 characters
     * while keeping identifying characteristics.
     */
    private String createName(IsolatedDockerAgentRequest r, String suffix, Integer maxLength) {
        // 50 is magic constant that attempts to limit the overall length of the name.
        String key = r.getResultKey();
        String name = suffix.isEmpty() ? key : key + "-" + suffix;

        int maxNameLength = maxLength - r.getUniqueIdentifier().toString().length() - 1;
        if (name.length() > maxNameLength) {
            PlanResultKey prk = PlanKeys.getPlanResultKey(key);
            String pk = prk.getPlanKey().toString();
            int len = maxNameLength - ("" + prk.getBuildNumber()).length() - 1;
            if (suffix.isEmpty()) {
                name = pk.substring(0, Math.min(pk.length(), len)) + "-" + prk.getBuildNumber();
            } else {
                // Remove the length of the suffix and a '-'
                len -= suffix.length() + 1;
                name = pk.substring(0, Math.min(pk.length(), len)) + "-" + prk.getBuildNumber() + "-" + suffix;
            }
        }
        // together with the identifier we are not at 88 max,in BUILDENG-15619 the failure in CNI plugin network
        // creation
        // was linked both to pod name and namespace lengths. this way we should accomodate fairly long namespace names.
        return name.toLowerCase(Locale.ENGLISH) + "-" + r.getUniqueIdentifier();
    }

    private Object createSpec(IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("restartPolicy", "Never");
        // 63 is max - https://tools.ietf.org/html/rfc2181#section-11
        String hostname = StringUtils.left(r.getResultKey().toLowerCase(Locale.ENGLISH), 63);
        if (hostname.length() == 63 && hostname.charAt(62) == '-') {
            hostname = hostname.substring(0, 62);
        }
        map.put("hostname", hostname);
        map.put("volumes", createVolumes(r));
        map.put("containers", createContainers(r));
        List<Map<String, Object>> initContainersList = new ArrayList<>();
        String currentSidekick = Objects.requireNonNull(
                globalConfiguration.getCurrentSidekick(),
                "Sidekick has not yet been configured! Please set it in the PBC Kubernetes Backend settings.");
        initContainersList.add(createSidekick(currentSidekick));
        map.put("initContainers", initContainersList);
        map.put("hostAliases", createLocalhostAliases(r.getConfiguration()));

        return map;
    }

    private List<Map<String, Object>> createVolumes(IsolatedDockerAgentRequest r) {
        ImmutableList.Builder<Map<String, Object>> builder = ImmutableList.builder();
        if (GENERATE_SHM_VOLUME) {
            // workaround for low default of 64M in docker daemon.
            // https://docs.openshift.org/latest/dev_guide/shared_memory.html
            // since docker daemon 17.06
            // the default size should be configurable on the docker daemon size and is likely preferable.
            builder.add(ImmutableMap.of("name", "shm", "emptyDir", ImmutableMap.of("medium", "Memory")));
        }
        builder.add(ImmutableMap.of("name", "workdir", "emptyDir", new HashMap<>()))
                .add(ImmutableMap.of("name", "pbcwork", "emptyDir", new HashMap<>()))
                .add(ImmutableMap.of("name", "logspool", "emptyDir", new HashMap<>()))
                .add(ImmutableMap.of("name", "bamboo-agent-sidekick", "emptyDir", new HashMap<>()));
        if (r.getConfiguration().isAwsRoleDefined()) {
            builder.add(ImmutableMap.<String, Object>builder()
                    .put("name", "aws-iam-token")
                    .put(
                            "projected",
                            ImmutableMap.builder()
                                    .put("defaultMode", 420)
                                    .put(
                                            "sources",
                                            ImmutableList.<Map<String, Object>>builder()
                                                    .add(ImmutableMap.of(
                                                            "secret",
                                                            ImmutableMap.builder()
                                                                    .put("name", createIrsaSecretName(r))
                                                                    .put(
                                                                            "items",
                                                                            ImmutableList.of(
                                                                                    ImmutableMap.of(
                                                                                            "key", "token", "path",
                                                                                            "token")))
                                                                    .build()))
                                                    .build())
                                    .build())
                    .build());
        }
        return builder.build();
    }

    private List<Map<String, Object>> createContainers(IsolatedDockerAgentRequest r) {
        ArrayList<Map<String, Object>> toRet = new ArrayList<>(createExtraContainers(r.getConfiguration()));
        toRet.add(createMainContainer(r));
        return toRet;
    }

    private Map<String, Object> createSidekick(String currentSidekick) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "bamboo-agent-sidekick");
        map.put("image", sanitizeImageName(currentSidekick));
        map.put("imagePullPolicy", IMAGE_PULL_POLICY);
        map.put(
                "command",
                ImmutableList.of(
                        "sh",
                        "-c",
                        "cp -r /buildeng/* /buildeng-data;" + "mkdir "
                                + PBC_DIR
                                + ";"
                                + "chmod a+wt "
                                + PBC_DIR
                                + ";"));
        map.put(
                "volumeMounts",
                ImmutableList.of(
                        ImmutableMap.of(
                                "name", "bamboo-agent-sidekick", "mountPath", "/buildeng-data", "readOnly", false),
                        ImmutableMap.of("name", "pbcwork", "mountPath", "/pbc", "readOnly", false)));
        map.put("resources", createResources(SIDEKICK_MEMORY, SIDEKICK_MEMORY, SIDEKICK_CPU));
        return map;
    }

    private static Map<String, Object> createResources(int softMemory, int hardMemory, int cpu) {
        return ImmutableMap.of(
                "limits",
                ImmutableMap.of("memory", "" + hardMemory + "Mi"),
                "requests",
                ImmutableMap.of("memory", "" + softMemory + "Mi", "cpu", "" + cpu + "m"));
    }

    /**
     * Overloaded createResources() specifically for creating extra containers.
     */
    private static Map<String, Object> createResources(int softMemory, int hardMemory, int cpu, int cpuLimit) {
        return ImmutableMap.of(
                "limits",
                ImmutableMap.of("memory", "" + hardMemory + "Mi", "cpu", "" + cpuLimit + "m"),
                "requests",
                ImmutableMap.of("memory", "" + softMemory + "Mi", "cpu", "" + cpu + "m"));
    }

    private Map<String, Object> createMainContainer(IsolatedDockerAgentRequest r) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", CONTAINER_NAME_BAMBOOAGENT);
        map.put("image", sanitizeImageName(r.getConfiguration().getDockerImage()));
        map.put("imagePullPolicy", IMAGE_PULL_POLICY);
        map.put("workingDir", WORK_DIR);
        map.put("command", ImmutableList.of("sh", "-c", "/buildeng/run-agent.sh"));
        map.put("env", createMainContainerEnvs(r));
        ImmutableList.Builder<Map<String, Object>> mountsBuilder = ImmutableList.<Map<String, Object>>builder()
                .add(ImmutableMap.of("name", "bamboo-agent-sidekick", "mountPath", WORK_DIR, "readOnly", false))
                .addAll(commonVolumeMounts());
        if (GENERATE_SHM_VOLUME) {
            mountsBuilder.add(ImmutableMap.of("name", "shm", "mountPath", "/dev/shm", "readOnly", false));
        }
        if (r.getConfiguration().isAwsRoleDefined()) {
            mountsBuilder.add(ImmutableMap.of(
                    "name", "aws-iam-token", "mountPath", AWS_WEB_IDENTITY_TOKEN_FILE, "readOnly", true));
        }
        map.put("volumeMounts", mountsBuilder.build());
        ContainerSizeDescriptor sizeDescriptor = globalConfiguration.getSizeDescriptor();
        map.put(
                "resources",
                createResources(
                        sizeDescriptor.getMemory(r.getConfiguration().getSize()),
                        sizeDescriptor.getMemoryLimit(r.getConfiguration().getSize()),
                        sizeDescriptor.getCpu(r.getConfiguration().getSize())));
        return map;
    }

    private static List<Map<String, Object>> commonVolumeMounts() {
        return ImmutableList.of(
                ImmutableMap.of("name", "workdir", "mountPath", BUILD_DIR, "readOnly", false),
                ImmutableMap.of("name", "pbcwork", "mountPath", "/pbc", "readOnly", false),
                ImmutableMap.of("name", "logspool", "mountPath", LOG_SPOOL_DIR, "readOnly", false));
    }

    private Object createMainContainerEnvs(IsolatedDockerAgentRequest r) {
        List<Map<String, Object>> envs = new ArrayList<>();
        Optional<Configuration.ExtraContainer> optDind = r.getConfiguration().getExtraContainers().stream()
                .filter((Configuration.ExtraContainer t) -> isDockerInDockerImage(t.getImage()))
                .findFirst();
        if (optDind.isPresent()) {
            envs.add(ImmutableMap.of(
                    "name", "DOCKER_HOST", "value", "tcp://" + optDind.get().getName() + ":2375"));
        }
        envs.add(ImmutableMap.of(
                "name", ENV_VAR_IMAGE, "value", r.getConfiguration().getDockerImage()));
        envs.add(ImmutableMap.of("name", ENV_VAR_RESULT_ID, "value", r.getResultKey()));
        envs.add(ImmutableMap.of("name", ENV_VAR_SERVER, "value", globalConfiguration.getBambooBaseUrl()));
        envs.add(ImmutableMap.of("name", "EPHEMERAL", "value", isBuildEphemeral()));
        envs.add(ImmutableMap.of(
                "name", "HEARTBEAT", "value", Integer.toString(globalConfiguration.getAgentHeartbeatTime())));
        envs.add(ImmutableMap.of("name", ARG_SECURITY_TOKEN, "value", r.getSecurityToken()));
        envs.add(ImmutableMap.of("name", "QUEUE_TIMESTAMP", "value", "" + r.getQueueTimestamp()));
        envs.add(ImmutableMap.of("name", "SUBMIT_TIMESTAMP", "value", "" + System.currentTimeMillis()));
        envs.add(ImmutableMap.of("name", "KUBE_POD_NAME", "value", createPodName(r)));
        envs.add(ImmutableMap.of(
                "name",
                KUBE_NUM_EXTRA_CONTAINERS,
                "value",
                "" + r.getConfiguration().getExtraContainers().size()));
        if (r.getConfiguration().isAwsRoleDefined()) {
            String awsRole = r.getConfiguration().getAwsRole();
            envs.add(ImmutableMap.of("name", ENV_AWS_ROLE_ARN, "value", awsRole));
            envs.add(ImmutableMap.of("name", ENV_AWS_WEB_IDENTITY, "value", AWS_WEB_IDENTITY_TOKEN_FILE + "token"));
        }
        return envs;
    }

    private String isBuildEphemeral() {
        if (darkFeatureManager
                .isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED)
                .orElse(false)) {
            return "true";
        } else {
            return "false";
        }
    }

    private static Object createLocalhostAliases(Configuration c) {
        List<Map<String, Object>> ips = new ArrayList<>();
        ips.add(ImmutableMap.of("ip", "127.0.0.1", "hostnames", containerNames(c)));
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
        map.put(
                "exec",
                ImmutableMap.of(
                        "command", ImmutableList.of("/bin/sh", "-c", "touch " + PBC_DIR + "/" + containerName)));
        return Collections.singletonMap("postStart", map);
    }

    public static String sanitizeImageName(String image) {
        return image.trim();
    }
}
