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

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.buildeng.kubernetes.jmx.JmxJob;
import com.atlassian.buildeng.kubernetes.jmx.KubeJmxService;
import com.atlassian.buildeng.kubernetes.shell.JavaShellExecutor;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.KubernetesHelper;
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
import java.util.Date;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Kubernetes implementation of backend PBC service.
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
    private final PluginScheduler pluginScheduler;
    private final ExecutorService executor;
    private final SubjectIdService subjectIdService;

    public KubernetesIsolatedDockerImpl(GlobalConfiguration globalConfiguration, 
            PluginScheduler pluginScheduler, KubeJmxService kubeJmxService, SubjectIdService subjectIdService) {
        this.pluginScheduler = pluginScheduler;
        this.globalConfiguration = globalConfiguration;
        this.kubeJmxService = kubeJmxService;
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
            Map<String, Object> finalPod = mergeMap(template, podDefinition);
            List<Map<String, Object>> podSpecList = new ArrayList<>();
            podSpecList.add(finalPod);

            if (request.getConfiguration().isAwsRoleDefined()) {
                Map<String, Object> iamRequest = PodCreator.createIamRequest(request, globalConfiguration, subjectId);
                Map<String, Object> iamRequestTemplate = loadTemplateIamRequest();

                Map<String, Object> finalIamRequest = mergeMap(iamRequestTemplate, iamRequest);
                //Temporary Workaround until we fully migrate to IRSA
                removeDefaultRole(finalPod);
                podSpecList.add(finalIamRequest) ;
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

        } catch (KubernetesClient.ClusterRegistryKubectlException e) {
            IsolatedDockerAgentResult result = new IsolatedDockerAgentResult();
            logger.error("Cluster Registry error:" + e.getMessage());
            callback.handle(result.withRetryRecoverable("Cluster Registry failure: " + e.getMessage()));
        } catch (KubernetesClient.KubectlException e) {
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
    void handleKubeCtlException(IsolatedDockerRequestCallback callback, KubernetesClient.KubectlException e) {
        IsolatedDockerAgentResult result = new IsolatedDockerAgentResult();
        if (e.getCause() instanceof IOException || e.getCause() instanceof InterruptedException) {
            logger.error("error", e);
            callback.handle(new IsolatedDockerAgentException(e));
        } else {
            // TODO move kubectl error message parsing close to code that invokes kubectl
            if (e.getMessage().contains("(AlreadyExists)")) {
                //full error message example:
                //Error from server (AlreadyExists): error when creating ".../pod1409421494114698314yaml":
                //object is being deleted: pods "plantemplates-srt-job1-..." already exists
                result = result.withRetryRecoverable(e.getMessage());
            } else if (e.getMessage().contains("(Timeout)")) {
                //full error message example:
                //Error from server (Timeout): error when creating ".../pod158999025779701949yaml":
                // Timeout: request did not complete within allowed duration
                result = result.withRetryRecoverable(e.getMessage());
            } else if (e.getMessage().contains("exceeded quota")) {
                //full error message example:
                //error when creating "pod.yaml": pods "test-pod" is forbidden:
                //exceeded quota: pod-demo, requested: pods=1, used: pods=2, limited: pods=2
                result = result.withRetryRecoverable(e.getMessage());
            } else {
                result = result.withError(e.getMessage());
            }
            callback.handle(result);
            logger.error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplatePod() {
        Yaml yaml =  new Yaml(new SafeConstructor());
        return (Map<String, Object>) yaml.load(globalConfiguration.getPodTemplateAsString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplateIamRequest() {
        Yaml yaml =  new Yaml(new SafeConstructor());
        return (Map<String, Object>) yaml.load(globalConfiguration.getBandanaIamRequestTemplateAsString());
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        config.put("globalConfiguration", globalConfiguration);
        config.put("isolatedAgentService", this);
        config.put("kubeJmxService", kubeJmxService);
        pluginScheduler.scheduleJob(PLUGIN_JOB_KEY, KubernetesWatchdog.class,
                config, new Date(), PLUGIN_JOB_INTERVAL_MILLIS);
        pluginScheduler.scheduleJob(PLUGIN_JOB_JMX_KEY, JmxJob.class, 
                config, new Date(), PLUGIN_JOB_JMX_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(PLUGIN_JOB_KEY);
        pluginScheduler.unscheduleJob(PLUGIN_JOB_JMX_KEY);
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
                merged.put(t, mergeMap((Map)originalEntry, (Map)u));
            } else if (originalEntry instanceof Collection && u instanceof Collection) {
                ArrayList<Map<String, Object>> lst = new ArrayList<>();

                if (t.equals("containers")) {
                    mergeById("name", lst, 
                            (Collection<Map<String, Object>>)originalEntry, (Collection<Map<String, Object>>)u);
                } else if (t.equals("hostAliases")) {
                    mergeById("ip", lst, 
                            (Collection<Map<String, Object>>)originalEntry, (Collection<Map<String, Object>>)u);
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
                return Pair.make(t, (URL)null);
            }
        }).filter((Pair t) -> t.getSecond() != null)
          .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }
    
    
}
