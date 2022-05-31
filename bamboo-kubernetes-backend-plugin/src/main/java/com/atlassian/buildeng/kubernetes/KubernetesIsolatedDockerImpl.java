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

package com.atlassian.buildeng.kubernetes;

import static com.atlassian.buildeng.isolated.docker.Constants.DEFAULT_ARCHITECTURE;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.kubernetes.exception.ClusterRegistryKubectlException;
import com.atlassian.buildeng.kubernetes.exception.KubectlException;
import com.atlassian.buildeng.kubernetes.jmx.JmxJob;
import com.atlassian.buildeng.kubernetes.jmx.KubeJmxService;
import com.atlassian.buildeng.kubernetes.shell.JavaShellExecutor;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.features.DarkFeatureManager;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import org.eclipse.jkube.kit.common.util.KubernetesHelper;
import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.utils.URIBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Kubernetes implementation of backend PBC service.
 *
 * @author mkleint
 */
public class KubernetesIsolatedDockerImpl implements IsolatedAgentService, LifecycleAware {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesIsolatedDockerImpl.class);

    public static final String RESULT_PREFIX = "result.isolated.docker.";
    private static final String URL_POD_NAME = "POD_NAME";
    private static final String URL_CONTAINER_NAME = "CONTAINER_NAME";
    static final String UID = "uid";
    public static final String NAME = "name";

    private static final String PLUGIN_JOB_KEY = "KubernetesIsolatedDockerImpl";
    private static final long PLUGIN_JOB_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final String PLUGIN_JOB_JMX_KEY = "KubeJmxService";
    private static final long PLUGIN_JOB_JMX_INTERVAL_MILLIS = Duration.ofSeconds(20).toMillis();

    private final GlobalConfiguration globalConfiguration;
    private final KubeJmxService kubeJmxService;
    private final Scheduler scheduler;
    private final ExecutorService executor;
    private final SubjectIdService subjectIdService;
    private final BandanaManager bandanaManager;
    private final DarkFeatureManager darkFeatureManager;

    public KubernetesIsolatedDockerImpl(GlobalConfiguration globalConfiguration,
                                        Scheduler scheduler,
                                        KubeJmxService kubeJmxService,
                                        SubjectIdService subjectIdService,
                                        BandanaManager bandanaManager,
                                        DarkFeatureManager darkFeatureManager) {
        this.scheduler = scheduler;
        this.globalConfiguration = globalConfiguration;
        this.kubeJmxService = kubeJmxService;
        this.bandanaManager = bandanaManager;
        this.darkFeatureManager = darkFeatureManager;
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5, 5,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        tpe.allowCoreThreadTimeOut(true);
        executor = tpe;
        this.subjectIdService = subjectIdService;
    }

    @Override
    public void startAgent(IsolatedDockerAgentRequest request, final IsolatedDockerRequestCallback callback) {
        logger.debug("Kubernetes received request for " + request.getResultKey());
        String subjectId = getSubjectId(request);
        executor.submit(() -> {
            exec(request, callback, subjectId);
        });
    }

    private void exec(IsolatedDockerAgentRequest request, final IsolatedDockerRequestCallback callback,
                      String subjectId) {
        logger.debug("Kubernetes processing request for " + request.getResultKey());
        try {
            Map<String, Object> template = loadTemplatePod();
            Map<String, Object> podDefinition = PodCreator.create(request, globalConfiguration);
            Map<String, Object> podWithoutArchOverrides = mergeMap(template, podDefinition);

            Map<String, Object> finalPod;
            if (darkFeatureManager.isEnabledForAllUsers("pbc.architecture.support").orElse(false)) {
                finalPod = addArchitectureOverrides(request, podWithoutArchOverrides);
            } else {
                finalPod = podWithoutArchOverrides;
            }

            List<Map<String, Object>> podSpecList = new ArrayList<>();
            podSpecList.add(finalPod);

            if (request.getConfiguration().isAwsRoleDefined()) {
                Map<String, Object> iamRequest = PodCreator.createIamRequest(request, globalConfiguration, subjectId);
                Map<String, Object> iamRequestTemplate = loadTemplateIamRequest();

                Map<String, Object> finalIamRequest = mergeMap(iamRequestTemplate, iamRequest);
                //Temporary Workaround until we fully migrate to IRSA
                removeDefaultRole(finalPod);
                podSpecList.add(finalIamRequest);
            }

            File podFile = createPodFile(podSpecList);

            Pod pod = new KubernetesClient(globalConfiguration, new JavaShellExecutor()).createPod(podFile);
            Duration servedIn = Duration.ofMillis(System.currentTimeMillis() - request.getQueueTimestamp());
            String name = KubernetesHelper.getName(pod);
            logger.info("Kubernetes successfully processed request for {} in {}, pod name: {}",
                    request.getResultKey(), servedIn, name);
            callback.handle(new IsolatedDockerAgentResult()
                    .withCustomResultData(NAME, name)
                    .withCustomResultData(UID, pod.getMetadata().getUid()));

        } catch (ClusterRegistryKubectlException e) {
            IsolatedDockerAgentResult result = new IsolatedDockerAgentResult();
            logger.error("Cluster Registry error:" + e.getMessage());
            callback.handle(result.withRetryRecoverable("Cluster Registry failure: " + e.getMessage()));
        } catch (KubectlException e) {
            handleKubeCtlException(callback, e);
        } catch (IOException e) {
            logger.error("io error", e);
            callback.handle(new IsolatedDockerAgentException(e));
        } catch (Throwable e) {
            //org.eclipse.gemini.blueprint.service.importer.ServiceProxyDestroyedException
            //is occassionally thrown when live reloading plugins. reattempt later.
            //do a dummy name check, not clear how this dependency is even pulled into bamboo,
            //it's likely part of a plugin only and we would not have the class in question on classpath anyway
            if (e.getClass().getSimpleName().equals("ServiceProxyDestroyedException")) {
                IsolatedDockerAgentResult result = new IsolatedDockerAgentResult();
                logger.warn("OSGi plugin system binding error:" + e.getMessage());
                callback.handle(result.withRetryRecoverable("PBC plugin was reloading/upgrading: " + e.getMessage()));
            } else {
                logger.error("unknown error", e);
                callback.handle(new IsolatedDockerAgentException(e));
            }
        }
    }

    @VisibleForTesting
    Map<String, Object> addArchitectureOverrides(IsolatedDockerAgentRequest request, Map<String, Object> podWithoutArchOverrides) {
        Map<String, Object> archConfig = loadArchitectureConfig();

        if (archConfig.isEmpty()) {
            return podWithoutArchOverrides;
        } else {
            if (request.getConfiguration().isArchitectureDefined()) {
                String architecture = request.getConfiguration().getArchitecture();
                if (archConfig.containsKey(architecture)) { // Architecture matches one in the Kubernetes pod overrides
                    return mergeMap(podWithoutArchOverrides, getSpecificArchConfig(archConfig, architecture));
                } else {
                    String supportedArchs = com.atlassian.buildeng.isolated.docker.GlobalConfiguration
                            .getArchitectureConfigWithBandana(bandanaManager).keySet().toString();
                    throw new IllegalArgumentException("Architecture specified in build configuration was not "
                            + "found in server's allowed architectures list! Supported architectures are: "
                            + supportedArchs);
                }
            } else { // Architecture is not specified at all
                return mergeMap(podWithoutArchOverrides, getSpecificArchConfig(archConfig,
                        getDefaultArchitectureName(archConfig)));
            }
        }
    }

    @VisibleForTesting
    String getSubjectId(IsolatedDockerAgentRequest request) {
        String subjectId;
        if (request.isPlan()) {
            subjectId = subjectIdService.getSubjectId(PlanKeys.getPlanKey(request.getResultKey()));
        } else {
            // Result Key comes in the format projectId-EnvironmentId-ResultId, we just need the project Id
            Long deploymentId = Long.parseLong(request.getResultKey().split("-")[0]);
            subjectId = subjectIdService.getSubjectId(deploymentId);
        }
        return subjectId;
    }

    @VisibleForTesting
    void handleKubeCtlException(IsolatedDockerRequestCallback callback, KubectlException e) {
        IsolatedDockerAgentResult result = new IsolatedDockerAgentResult();
        if (e.getCause() instanceof IOException || e.getCause() instanceof InterruptedException) {
            logger.error("error", e);
            callback.handle(new IsolatedDockerAgentException(e));
        } else if (e.isRecoverable()) {
            result = result.withRetryRecoverable(e.getMessage());
        } else {
            result = result.withError(e.getMessage());
        }
        callback.handle(result);
        logger.error(e.getMessage());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplatePod() {
        Yaml yaml = new Yaml(new SafeConstructor());
        return (Map<String, Object>) yaml.load(globalConfiguration.getPodTemplateAsString());
    }

    private Map<String, Object> loadArchitectureConfig() {
        String archConfig = globalConfiguration.getBandanaArchitecturePodConfig();

        if (StringUtils.isBlank(archConfig)) {
            return Collections.emptyMap();
        } else {
            Yaml yaml = new Yaml(new SafeConstructor());
            return (Map<String, Object>) yaml.load(archConfig);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplateIamRequest() {
        Yaml yaml = new Yaml(new SafeConstructor());
        return (Map<String, Object>) yaml.load(globalConfiguration.getBandanaIamRequestTemplateAsString());
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
        JobDataMap config = new JobDataMap();
        config.put("globalConfiguration", globalConfiguration);
        config.put("isolatedAgentService", this);
        config.put("kubeJmxService", kubeJmxService);

        Trigger watchdogJobTrigger = jobTrigger(PLUGIN_JOB_INTERVAL_MILLIS);
        JobDetail watchdogJob = jobDetail(KubernetesWatchdog.class, PLUGIN_JOB_KEY, config);
        Trigger pluginJmxJobTrigger = jobTrigger(PLUGIN_JOB_JMX_INTERVAL_MILLIS);
        JobDetail pluginJmxJob = jobDetail(JmxJob.class, PLUGIN_JOB_JMX_KEY, config);

        logger.info("Kubernetes Backend plugin started. Scheduling jobs.");
        try {
            scheduler.scheduleJob(watchdogJob, watchdogJobTrigger);
        } catch (SchedulerException e) {
            logger.error("Unable to schedule KubernetesWatchdog", e);
        }
        try {
            scheduler.scheduleJob(pluginJmxJob, pluginJmxJobTrigger);
        } catch (SchedulerException e) {
            logger.error("Unable to schedule JmxJob", e);
        }
    }

    private Trigger jobTrigger(long interval) {
        return newTrigger()
                .startNow()
                .withSchedule(simpleSchedule()
                        .withIntervalInMilliseconds(interval)
                        .repeatForever()
                )
                .build();
    }

    private JobDetail jobDetail(Class c, String jobKey, JobDataMap jobDataMap) {
        return newJob(c)
                .withIdentity(jobKey)
                .usingJobData(jobDataMap)
                .build();
    }

    @Override
    public void onStop() {
        logger.info("Kubernetes Backend plugin unloaded. Deleting jobs.");
        try {
            boolean watchdogJobDeletion = scheduler.deleteJob(JobKey.jobKey(PLUGIN_JOB_KEY));
            if (!watchdogJobDeletion) {
                logger.warn("Was not able to delete KubernetesWatchdog job. Was it already deleted?");
            }
        } catch (SchedulerException e) {
            logger.error("Kubernetes Isolated Docker Plugin being stopped but unable to delete KubernetesWatchdogJob", e);
        }
        try {
            boolean jmxJobDeletion = scheduler.deleteJob(JobKey.jobKey(PLUGIN_JOB_JMX_KEY));
            if (!jmxJobDeletion) {
                logger.warn("Was not able to delete Kubernetes JMX job. Was it already deleted?");
            }
        } catch (SchedulerException e) {
            logger.error("Kubernetes Isolated Docker Plugin being stopped but unable to delete JmxJob", e);
        }
        executor.shutdown();
    }

    private File createPodFile(List<Map<String, Object>> podSpecList) throws IOException {
        File f = File.createTempFile("pod", "yaml");
        writeSpecToFile(podSpecList, f);
        return f;
    }

    //A hacky way to remove a default role being provided by kube2iam
    //Will remove once we fully migrate to IRSA
    private void removeDefaultRole(Map<String, Object> finalPod) throws IOException {
        if (finalPod.containsKey("metadata")) {
            Map<String, Object> metadata = (Map<String, Object>) finalPod.get("metadata");
            if (metadata.containsKey("annotations")) {
                Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
                annotations.remove("iam.amazonaws.com/role");
            }
        }
    }

    private void writeSpecToFile(List<Map<String, Object>> document, File f) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setExplicitStart(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.SINGLE_QUOTED);
        options.setIndent(4);
        options.setCanonical(false);
        Yaml yaml = new Yaml(options);

        logger.debug("YAML----------");
        logger.debug(yaml.dumpAll(document.iterator()));
        logger.debug("YAMLEND----------");
        FileUtils.write(f, yaml.dumpAll(document.iterator()), "UTF-8", false);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mergeMap(Map<String, Object> template, Map<String, Object> overrides) {
        final Map<String, Object> merged = new HashMap<>(template);
        overrides.forEach((String t, Object u) -> {
            Object originalEntry = merged.get(t);
            if (originalEntry instanceof Map && u instanceof Map) {
                merged.put(t, mergeMap((Map) originalEntry, (Map) u));
            } else if (originalEntry instanceof Collection && u instanceof Collection) {
                ArrayList<Map<String, Object>> lst = new ArrayList<>();

                if (t.equals("containers")) {
                    mergeById("name", lst,
                            (Collection<Map<String, Object>>) originalEntry, (Collection<Map<String, Object>>) u);
                } else if (t.equals("hostAliases")) {
                    mergeById("ip", lst,
                            (Collection<Map<String, Object>>) originalEntry, (Collection<Map<String, Object>>) u);
                } else {
                    lst.addAll((Collection) originalEntry);
                    lst.addAll((Collection) u);
                }
                merged.put(t, lst);
            } else {
                merged.put(t, u);
            }
        });
        return merged;
    }

    private static void mergeById(String id, ArrayList<Map<String, Object>> lst,
                                  Collection<Map<String, Object>> originalEntry, Collection<Map<String, Object>> u) {
        Map<String, Map<String, Object>> containers1 = originalEntry
                .stream().collect(Collectors.toMap(x -> (String) x.get(id), x -> x));
        Map<String, Map<String, Object>> containers2 = u
                .stream().collect(Collectors.toMap(x -> (String) x.get(id), x -> x));

        containers1.forEach((String name, Map<String, Object> container1) -> {
            Map<String, Object> container2 = containers2.remove(name);
            if (container2 != null) {
                lst.add(mergeMap(container1, container2));
            } else {
                lst.add(container1);
            }
        });
        lst.addAll(containers2.values());
    }

    @Override
    public Map<String, URL> getContainerLogs(Configuration configuration, Map<String, String> customData) {
        String url = globalConfiguration.getPodLogsUrl();
        String podName = customData.get(RESULT_PREFIX + NAME);
        if (StringUtils.isBlank(url) || StringUtils.isBlank(podName)) {
            return Collections.emptyMap();
        }
        return PodCreator.containerNames(configuration).stream().map((String t) -> {
            String resolvedUrl = url.replace(URL_CONTAINER_NAME, t).replace(URL_POD_NAME, podName);
            try {
                URIBuilder bb = new URIBuilder(resolvedUrl);
                return Pair.make(t, bb.build().toURL());
            } catch (URISyntaxException | MalformedURLException ex) {
                logger.error("KUbernetes logs URL cannot be constructed from template:" + resolvedUrl, ex);
                return Pair.make(t, (URL) null);
            }
        }).filter((Pair t) -> t.getSecond() != null)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    @VisibleForTesting
    Map<String, Object> getSpecificArchConfig(Map<String, Object> archConfig, String s) {
        return (Map<String, Object>) ((Map<String, Object>) archConfig.get(s)).get("config");
    }

    @VisibleForTesting
    String getDefaultArchitectureName(Map<String, Object> archConfig) {
        return (String) archConfig.get(DEFAULT_ARCHITECTURE);
    }

}
