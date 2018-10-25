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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KubernetesClient {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesClient.class);
    
    private final ContextSupplier globalSupplier = new GlobalContextSupplier();
    private final LoadingCache<String, List<ClusterRegistryItem>> cache = 
            CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build(new CacheLoader<String, List<ClusterRegistryItem>>() {
                    @Override
                    public List<ClusterRegistryItem> load(String key) throws Exception {
                        return loadClusters();
                    }
                });

    private final GlobalConfiguration globalConfiguration;
    
    private static final String ERROR_MESSAGE_PREFIX = "kubectl returned non-zero exit code.";
    private static final String PROP_CONTEXT = "context";

    KubernetesClient(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }

    private Object executeKubectlAsJson(ContextSupplier contextHandler, String... args)
            throws KubectlException {
        try {
            return KubernetesHelper.loadJson(executeKubectl(contextHandler, 
                    Lists.asList("-o", "json", args).toArray(new String[0])));
        } catch (IOException x) {
            throw new KubectlException("" + x.getMessage(), x);
        }
    }

    private String executeKubectl(ContextSupplier contextSupplier, String... args)
            throws KubectlException {
        List<String> kubectlArgs = new ArrayList<>(Arrays.asList(args));
        kubectlArgs.add(0, Constants.KUBECTL_GLOBAL_OPTIONS);
        kubectlArgs.add(0, Constants.KUBECTL_EXECUTABLE);
        if (contextSupplier != null && contextSupplier.getValue() != null) {
            kubectlArgs.addAll(Arrays.asList("--context", contextSupplier.getValue()));
        }
        logger.debug("Executing " + kubectlArgs);
        int ret;
        String output;
        try {
            ProcessBuilder pb = new ProcessBuilder(kubectlArgs);
            pb.redirectErrorStream(true);
            // kubectl requires HOME env to find the config, but the Bamboo server JVM might not have it setup.
            pb.environment().put("HOME", System.getProperty("user.home"));
            Process process = pb.start();
            output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);

            ret = process.waitFor();
        } catch (IOException | InterruptedException x) {
            throw new KubectlException("" + x.getMessage(), x);
        }
        if (ret != 0) {
            throw new KubectlException(ERROR_MESSAGE_PREFIX + " Output: " + output);
        }
        return output;
    }
    
    @SuppressWarnings("unchecked")
    List<Pod> getPods(String labelName, String labelValue)
            throws KubectlException {
        String label = labelName + '=' + labelValue;
        if (globalConfiguration.isUseClusterRegistry()) {
            return availableClusterRegistryContexts().stream()
                   .flatMap((String t) -> getPodStream(label, new SimpleContextSupplier(t)))
                   .collect(Collectors.toList());
        } else {
            return getPodStream(label, globalSupplier).collect(Collectors.toList());
        }
    }
    
    private Stream<Pod> getPodStream(String label, ContextSupplier contextHandler) 
            throws KubectlException {
        return ((KubernetesList) executeKubectlAsJson(contextHandler, "get", "pods", "--selector", label))
                .getItems().stream()
                    .map((HasMetadata pod) -> (Pod) pod)
                    .map((Pod t) -> {
                        t.setAdditionalProperty(PROP_CONTEXT, contextHandler.getValue());
                        return t;
                    });
    }

    @SuppressWarnings("unchecked")
    Pod createPod(File podFile)
            throws KubectlException {
        Pod pod;
        ContextSupplier supplier;
        if (globalConfiguration.isUseClusterRegistry()) {
            List<String> primary = primaryClusterRegistryContexts();
            if (primary.isEmpty()) {
                primary = availableClusterRegistryContexts();
            }
            Collections.shuffle(primary);
            if (primary.isEmpty()) {
                throw new KubectlException("Found no cluster available in cluster registry");
            } else {
                supplier = new SimpleContextSupplier(primary.get(0));
            }
        } else {
            supplier = globalSupplier;
        }
        pod = (Pod) executeKubectlAsJson(supplier, "create", "--validate=false", "-f", podFile.getAbsolutePath());
        pod.setAdditionalProperty(PROP_CONTEXT, globalSupplier.getValue());
        return pod;
    }

    String describePod(Pod pod)
            throws KubectlException {
        return executeKubectl(new PodContextSupplier(pod), "describe", "pod", KubernetesHelper.getName(pod));
    }

    void deletePod(Pod pod)
            throws KubectlException {
        executeKubectl(new PodContextSupplier(pod), 
                "delete", "pod", "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT, KubernetesHelper.getName(pod));
    }


    void deletePod(String podName)
            throws InterruptedException, IOException, KubectlException {
        ContextSupplier supplier;
        if (globalConfiguration.isUseClusterRegistry()) {
            availableClusterRegistryContexts().forEach((String t) -> {
                try {
                    executeKubectl(new SimpleContextSupplier(t), 
                            "delete", "pod", "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT, podName);
                } catch (KubectlException x) {
                    if (x.getMessage() != null && x.getMessage().startsWith(ERROR_MESSAGE_PREFIX)) {
                        logger.debug("swallowing error because we are executing in multiple clusters", x);
                    } else {
                        throw x;
                    }
                }
            });
        } else {
            supplier = globalSupplier;
            executeKubectl(supplier, 
                    "delete", "pod", "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT, podName);
        }
    }

    private List<ClusterRegistryItem> getClusters() throws KubectlException {
        return cache.getUnchecked("cachedValue");
    }

    private List<ClusterRegistryItem> loadClusters() throws KubectlException {
        String json = executeKubectl(globalSupplier, "get", "clusters", "-o", "json");
        //TODO check in future if clusterregistry.k8s.io/v1alpha1 / Cluster is supported by the client lib parsing
        JsonParser parser = new JsonParser();
        JsonElement root = parser.parse(json);
        List<ClusterRegistryItem> items = new ArrayList<>();
        if (root != null && root.isJsonObject()) {
            JsonObject rootObj = root.getAsJsonObject();
            if (rootObj.has("items") && rootObj.has("kind")) {
                JsonArray array = rootObj.getAsJsonArray("items");
                if (array != null) {
                    for (Iterator iterator = array.iterator(); iterator.hasNext();) {
                        JsonElement next = (JsonElement) iterator.next();
                        if (next != null && next.isJsonObject() 
                                && next.getAsJsonObject().has("kind")
                                && "Cluster".equals(next.getAsJsonObject().getAsJsonPrimitive("kind").getAsString())) {
                            JsonObject metadata = next.getAsJsonObject().getAsJsonObject("metadata");
                            String name = metadata.getAsJsonPrimitive("name").getAsString();
                            JsonObject labels = metadata.getAsJsonObject("labels");
                            List<Pair<String, String>> labelList = labels == null ? Collections.emptyList()
                                    : labels.entrySet().stream()
                                            .map((Map.Entry<String, JsonElement> t) -> 
                                                    new ImmutablePair<>(t.getKey(), 
                                                            t.getValue().getAsJsonPrimitive().getAsString()))
                                            .collect(Collectors.toList());
                            items.add(new ClusterRegistryItem(name, labelList));
                        }
                    }
                }
            }
        }
        return items;
    }



    private List<String> availableClusterRegistryContexts() throws KubectlException {
        Supplier<String> label = () -> globalConfiguration.getClusterRegistryAvailableClusterSelector();
        return registryContexts(label);
    }

    private List<String> registryContexts(Supplier<String> filter) 
            throws KubectlException {
        List<ClusterRegistryItem> clusters = getClusters();
        return clusters.stream()
                .filter((ClusterRegistryItem t) -> t.labels.stream()
                        .anyMatch((Pair<String, String> t1) -> StringUtils.equals(t1.getKey(), filter.get())))
                .map((ClusterRegistryItem t) -> 
                        t.labels.stream()
                                .filter((Pair<String, String> t1) -> 
                                        StringUtils.equals(t1.getKey(), 
                                                globalConfiguration.getClusterRegistryAvailableClusterSelector()))
                                .map((Pair<String, String> t1) -> t1.getValue())
                                .findFirst().orElse(null))
                                
                .filter((String t) -> t != null)
                .collect(Collectors.toList());
    }
    
    private List<String> primaryClusterRegistryContexts() throws KubectlException {
        Supplier<String> label;
        if (StringUtils.isNotBlank(globalConfiguration.getClusterRegistryPrimaryClusterSelector())) {
            label = () -> globalConfiguration.getClusterRegistryPrimaryClusterSelector();
        } else {
            label = () -> globalConfiguration.getClusterRegistryAvailableClusterSelector();
        }
        return registryContexts(label);
    }

    static class KubectlException extends RuntimeException {
        KubectlException(String message) {
            super(message);
        }

        public KubectlException(String message, Throwable cause) {
            super(cause);
        }
    }

    static class ClusterRegistryItem {
        final String name;
        final List<Pair<String, String>> labels;

        public ClusterRegistryItem(String name, List<Pair<String, String>> labels) {
            this.name = name;
            this.labels = labels;
        }
        
    }
    
    private interface ContextSupplier {
        String getValue();
    }
    
    private class GlobalContextSupplier implements ContextSupplier {

        @Override
        public String getValue() {
            return globalConfiguration.getCurrentContext();
        }
    }

    private static class SimpleContextSupplier implements ContextSupplier {
        private final String context;

        public SimpleContextSupplier(@Nullable String context) {
            this.context = context;
        }

        @Override
        public String getValue() {
            return context;
        }
    }

    private static class PodContextSupplier implements ContextSupplier {
        
        private final Pod pod;

        public PodContextSupplier(@Nonnull Pod pod) {
            this.pod = pod;
        }

        @Override
        public String getValue() {
            return (String) pod.getAdditionalProperties().get(PROP_CONTEXT);
        }
    }

}
