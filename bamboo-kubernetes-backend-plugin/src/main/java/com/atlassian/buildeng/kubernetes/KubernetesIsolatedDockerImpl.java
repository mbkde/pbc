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

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.buildeng.isolated.docker.scheduler.SchedulerUtils;
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
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jkube.kit.common.util.KubernetesHelper;
import org.jetbrains.annotations.NotNull;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;

/**
 * Kubernetes implementation of backend PBC service.
 *
 * @author mkleint
 */
@BambooComponent
@ExportAsService({KubernetesIsolatedDockerImpl.class, IsolatedAgentService.class, LifecycleAware.class})
public class KubernetesIsolatedDockerImpl implements IsolatedAgentService, LifecycleAware {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesIsolatedDockerImpl.class);

    public static final String RESULT_PREFIX = "result.isolated.docker.";
    private static final String URL_POD_NAME = "POD_NAME";
    private static final String URL_CONTAINER_NAME = "CONTAINER_NAME";
    static final String UID = "uid";
    public static final String NAME = "name";

    private static final JobKey PLUGIN_JOB_KEY = JobKey.jobKey("KubernetesIsolatedDockerImpl");
    private static final long PLUGIN_JOB_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final JobKey PLUGIN_JOB_JMX_KEY = JobKey.jobKey("KubeJmxService");
    private static final long PLUGIN_JOB_JMX_INTERVAL_MILLIS = Duration.ofSeconds(20).toMillis();

    private final GlobalConfiguration globalConfiguration;
    private final KubeJmxService kubeJmxService;
    private final Scheduler scheduler;
    private final ExecutorService executor;
    private final SubjectIdService subjectIdService;

    private final KubernetesPodSpecList podSpecList;

    @Inject
    public KubernetesIsolatedDockerImpl(GlobalConfiguration globalConfiguration,
            Scheduler scheduler,
            KubeJmxService kubeJmxService,
            SubjectIdService subjectIdService,
            KubernetesPodSpecList podSpecList) {
        this.scheduler = scheduler;
        this.globalConfiguration = globalConfiguration;
        this.kubeJmxService = kubeJmxService;
        this.subjectIdService = subjectIdService;
        this.podSpecList = podSpecList;

        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        tpe.allowCoreThreadTimeOut(true);
        executor = tpe;
    }

    @Override
    public void startAgent(IsolatedDockerAgentRequest request, final IsolatedDockerRequestCallback callback) {
        logger.debug("Kubernetes received request for " + request.getResultKey());
        String subjectId = getSubjectId(request);
        executor.submit(() -> exec(request, callback, subjectId));
    }

    private Pod createPod(File podFile) throws KubectlException {
        return new KubernetesClient(globalConfiguration, new JavaShellExecutor()).createPod(podFile);
    }

    private void handleCallback(IsolatedDockerRequestCallback callback, Pod pod, String name) {
        callback.handle(new IsolatedDockerAgentResult()
                .withCustomResultData(NAME, name)
                .withCustomResultData(UID, pod.getMetadata().getUid()));
    }

    @VisibleForTesting
    void exec(IsolatedDockerAgentRequest request, final IsolatedDockerRequestCallback callback, String subjectId) {
        logger.debug("Kubernetes processing request for " + request.getResultKey());
        try {
            File podFile = this.podSpecList.generate(request, subjectId);
            Pod pod = createPod(podFile);

            Duration servedIn = Duration.ofMillis(System.currentTimeMillis() - request.getQueueTimestamp());
            String name = KubernetesHelper.getName(pod);
            logger.info("Kubernetes successfully processed request for {} in {}, pod name: {}",
                    request.getResultKey(),
                    servedIn,
                    name);
            podSpecList.cleanUp(podFile);
            handleCallback(callback, pod, name);
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
            // org.eclipse.gemini.blueprint.service.importer.ServiceProxyDestroyedException
            // is occasionally thrown when live reloading plugins. reattempt later.
            // do a dummy name check, not clear how this dependency is even pulled into
            // bamboo,
            // it's likely part of a plugin only, and we would not have the class in question
            // on classpath anyway
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
            // Result Key comes in the format projectId-EnvironmentId-ResultId, we just need
            // the project Id
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

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
        SchedulerUtils schedulerUtils = new SchedulerUtils(scheduler, logger);
        logger.info("PBC Kubernetes Backend plugin started. Checking that jobs from a prior instance of the" +
                " plugin are not still running.");
        List<JobKey> previousJobKeys = Arrays.asList(PLUGIN_JOB_KEY, PLUGIN_JOB_JMX_KEY);
        schedulerUtils.awaitPreviousJobExecutions(previousJobKeys);

        JobDataMap config = new JobDataMap();
        config.put("globalConfiguration", globalConfiguration);
        config.put("isolatedAgentService", this);
        config.put("kubeJmxService", kubeJmxService);

        JobDetail watchdogJob = jobDetail(KubernetesWatchdog.class, PLUGIN_JOB_KEY, config);
        JobDetail pluginJmxJob = jobDetail(JmxJob.class, PLUGIN_JOB_JMX_KEY, config);
        Trigger watchdogJobTrigger = jobTrigger(PLUGIN_JOB_INTERVAL_MILLIS);
        Trigger pluginJmxJobTrigger = jobTrigger(PLUGIN_JOB_JMX_INTERVAL_MILLIS);

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
                .withSchedule(simpleSchedule().withIntervalInMilliseconds(interval).repeatForever())
                .build();
    }

    private JobDetail jobDetail(Class<? extends org.quartz.Job> c, JobKey jobKey, JobDataMap jobDataMap) {
        return newJob(c).withIdentity(jobKey).usingJobData(jobDataMap).build();
    }

    @Override
    public void onStop() {
        logger.info("Kubernetes Backend plugin unloaded. Unscheduling jobs.");
        try {
            boolean watchdogJobUnschedule = scheduler.deleteJob(PLUGIN_JOB_KEY);
            if (!watchdogJobUnschedule) {
                logger.warn("Was not able to delete KubernetesWatchdog job. Was it already delete?");
            }
        } catch (SchedulerException e) {
            logger.error("Kubernetes Isolated Docker Plugin being stopped but unable to delete KubernetesWatchdogJob",
                    e);
        }
        try {
            boolean jmxJobUnschedule = scheduler.deleteJob(PLUGIN_JOB_JMX_KEY);
            if (!jmxJobUnschedule) {
                logger.warn("Was not able to delete Kubernetes JMX job. Was it already delete?");
            }
        } catch (SchedulerException e) {
            logger.error("Kubernetes Isolated Docker Plugin being stopped but unable to delete JmxJob", e);
        }
        executor.shutdown();
    }

    @Override
    public @NotNull Map<String, URL> getContainerLogs(Configuration configuration, Map<String, String> customData) {
        String url = globalConfiguration.getPodLogsUrl();
        String podName = customData.get(RESULT_PREFIX + NAME);
        if (StringUtils.isBlank(url) || StringUtils.isBlank(podName)) {
            return Collections.emptyMap();
        }
        return PodCreator
                .containerNames(configuration)
                .stream()
                .map((String t) -> {
                    String resolvedUrl = url.replace(URL_CONTAINER_NAME, t).replace(URL_POD_NAME, podName);
                    try {
                        URIBuilder bb = new URIBuilder(resolvedUrl);
                        return Pair.make(t, bb.build().toURL());
                    } catch (URISyntaxException | MalformedURLException ex) {
                        logger.error("Kubernetes logs URL cannot be constructed from template:" + resolvedUrl, ex);
                        return Pair.make(t, (URL) null);
                    }
                })
                .filter((Pair<String, URL> t) -> t.getSecond() != null)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

}
