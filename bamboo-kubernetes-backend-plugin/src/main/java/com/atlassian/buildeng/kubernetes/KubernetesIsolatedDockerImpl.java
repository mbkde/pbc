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

import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 *
 * @author mkleint
 */
public class KubernetesIsolatedDockerImpl implements IsolatedAgentService, LifecycleAware {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesIsolatedDockerImpl.class);

    private static final String PLUGIN_JOB_KEY = "KubernetesIsolatedDockerImpl";
    private static final long PLUGIN_JOB_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    private final GlobalConfiguration globalConfiguration;
    private final PluginScheduler pluginScheduler;
    //0-10 threads, destroyed after a minute if not used.
    final ExecutorService executor = new ThreadPoolExecutor(0, 10,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<>());

    public KubernetesIsolatedDockerImpl(GlobalConfiguration globalConfiguration, PluginScheduler pluginScheduler) {
        this.pluginScheduler = pluginScheduler;
        this.globalConfiguration = globalConfiguration;
    }

    @Override
    public void startAgent(IsolatedDockerAgentRequest request, final IsolatedDockerRequestCallback callback) {
        executor.submit(() -> {
            exec(request, callback);
        });
    }

    private void exec(IsolatedDockerAgentRequest request, final IsolatedDockerRequestCallback callback) {
        Map<String, Object> template = loadTemplatePod();
        Map<String, Object> podDefinition = PodCreator.create(request, globalConfiguration);
        Map<String, Object> finalPod = mergeMap(template, podDefinition);
        try {
            File podFile = createPodFile(finalPod);
            KubectlExecutor.Result ret = KubectlExecutor.startPod(podFile);
            if (ret.getResultCode() == 0) {
                callback.handle(new IsolatedDockerAgentResult()
                        .withCustomResultData("name", ret.getPodName())
                        .withCustomResultData("uid", ret.getPodUID()));
            } else {
                callback.handle(new IsolatedDockerAgentResult().withError("kubectl process exited with " + ret.getResultCode()));
            }
            logger.debug("KUBECTL: Ret value= " + ret);
        } catch (IOException | InterruptedException e) {
            logger.error("error", e);
            callback.handle(new IsolatedDockerAgentException(e));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplatePod() {
        Yaml yaml =  new Yaml(new SafeConstructor());
        return (Map<String, Object>) yaml.load(globalConfiguration.getPodTemplateAsString());
    }

    @Override
    public List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    @Override
    public void onStart() {
        Map<String, Object> config = new HashMap<>();
        config.put("globalConfiguration", globalConfiguration);
        pluginScheduler.scheduleJob(PLUGIN_JOB_KEY, KubernetesWatchdog.class, config, new Date(), PLUGIN_JOB_INTERVAL_MILLIS);
    }

    @Override
    public void onStop() {
        pluginScheduler.unscheduleJob(PLUGIN_JOB_KEY);
    }

    private File createPodFile(Map<String, Object> finalPod) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setCanonical(false);
        Yaml yaml = new Yaml(options);
        logger.debug("YAML----------");
        logger.debug(yaml.dump(finalPod));
        logger.debug("YAMLEND----------");
        File f = File.createTempFile("pod", "yaml");
        FileUtils.write(f, yaml.dump(finalPod), "UTF-8");
        return f;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMap(Map<String, Object> template, Map<String, Object> overrides) {
        final Map<String, Object> merged = new HashMap<>(template);
        overrides.forEach((String t, Object u) -> {
            Object originalEntry = merged.get(t);
            if (originalEntry instanceof Map && u instanceof Map) {
                merged.put(t, mergeMap((Map)originalEntry, (Map)u));
            } else if (originalEntry instanceof Collection && u instanceof Collection) {
                ArrayList<Object> lst = new ArrayList<>((Collection)originalEntry);
                lst.addAll((Collection)u);
                merged.put(t, lst);
            } else {
                merged.put(t, u);
            }
        });
        return merged;
    }
    
}
