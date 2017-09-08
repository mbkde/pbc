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

import com.atlassian.bamboo.utils.Pair;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
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
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    static final String RESULT_PREFIX = "result.isolated.docker.";
    private static final String URL_POD_NAME = "POD_NAME";
    private static final String URL_CONTAINER_NAME = "CONTAINER_NAME";
    static final String UID = "uid";
    static final String NAME = "name";

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
                        .withCustomResultData(NAME, ret.getPodName())
                        .withCustomResultData(UID, ret.getPodUid()));
            } else {
                callback.handle(new IsolatedDockerAgentResult()
                        .withError("kubectl:" + ret.getRawResponse()));
                logger.error("kubectl:" + ret.getRawResponse());
            }
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
        config.put("isolatedAgentService", this);
        pluginScheduler.scheduleJob(PLUGIN_JOB_KEY, KubernetesWatchdog.class,
                config, new Date(), PLUGIN_JOB_INTERVAL_MILLIS);
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
    static Map<String, Object> mergeMap(Map<String, Object> template, Map<String, Object> overrides) {
        final Map<String, Object> merged = new HashMap<>(template);
        overrides.forEach((String t, Object u) -> {
            Object originalEntry = merged.get(t);
            if (originalEntry instanceof Map && u instanceof Map) {
                merged.put(t, mergeMap((Map)originalEntry, (Map)u));
            } else if (originalEntry instanceof Collection && u instanceof Collection) {
                ArrayList<Map<String, Object>> lst = new ArrayList<>();

                if (t.equals("containers")) {
                    Map<String, Map<String, Object>> containers1 = ((Collection<Map<String, Object>>) originalEntry)
                            .stream().collect(Collectors.toMap(x -> (String) x.get("name"), x -> x));
                    Map<String, Map<String, Object>> containers2 = ((Collection<Map<String, Object>>) u)
                            .stream().collect(Collectors.toMap(x -> (String) x.get("name"), x -> x));

                    containers1.forEach((String name, Map<String, Object> container1) -> {
                        Map<String, Object> container2 = containers2.remove(name);
                        if (container2 != null) {
                            lst.add(mergeMap(container1, container2));
                        } else {
                            lst.add(container1);
                        }
                    });
                    lst.addAll(containers2.values());
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

    @Override
    public Map<String, URL> getContainerLogs(Configuration configuration, Map<String, String> customData) {
        String url = globalConfiguration.getPodLogsUrl();
        String podName = customData.get(RESULT_PREFIX + NAME);
        if (StringUtils.isBlank(url) || StringUtils.isBlank(podName)) {
            return Collections.emptyMap();
        }
        Stream<String> s = Stream.concat(Stream.of(PodCreator.CONTAINER_NAME_BAMBOOAGENT),configuration.getExtraContainers().stream()
                        .map((Configuration.ExtraContainer t) -> t.getName()));
        return s.map((String t) -> {
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
