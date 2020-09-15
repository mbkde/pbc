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

import com.atlassian.buildeng.kubernetes.serialization.JsonResponseMapper;
import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;
import com.atlassian.buildeng.kubernetes.serialization.StringResponseMapper;
import com.atlassian.buildeng.kubernetes.shell.ShellException;
import com.atlassian.buildeng.kubernetes.shell.ShellExecutor;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class KubernetesClient {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesClient.class);

    private final ContextSupplier globalSupplier = new GlobalContextSupplier();

    private ShellExecutor shellExecutor;

    private final GlobalConfiguration globalConfiguration;
    private StringResponseMapper defaultResponseMapper = new StringResponseMapper();

    private static final String ERROR_MESSAGE_PREFIX = "kubectl returned non-zero exit code.";
    private static final String PROP_CONTEXT = "context";
    private static final String CACHED_VALUE = "cachedValue";
    private final LoadingCache<String, List<ClusterRegistryItem>> cache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(10, TimeUnit.SECONDS)
                    .build(new CacheLoader<String, List<ClusterRegistryItem>>() {
                        @Override
                        public List<ClusterRegistryItem> load(String key) throws Exception {
                            return loadClusters();
                        }
                    });
    private JsonResponseMapper jsonResponseMapper = new JsonResponseMapper();

    KubernetesClient(GlobalConfiguration globalConfiguration, ShellExecutor shellExecutor) {
        this.globalConfiguration = globalConfiguration;
        this.shellExecutor = shellExecutor;
    }

    private Object executeKubectlAsObject(ContextSupplier contextHandler, String... args)
            throws KubectlException {
        return executeKubectlWithResponseMapper(contextHandler, jsonResponseMapper,
                Lists.asList("-o", "json", args).toArray(new String[0]));
    }

    private <T> T executeKubectlWithResponseMapper(ContextSupplier contextSupplier,
                                                   ResponseMapper<T> responseMapper,
                                                   String... args) throws KubectlException {
        List<String> kubectlArgs = new ArrayList<>(Arrays.asList(args));
        kubectlArgs.add(0, Constants.KUBECTL_GLOBAL_OPTIONS);
        kubectlArgs.add(0, Constants.KUBECTL_EXECUTABLE);
        if (contextSupplier != null && contextSupplier.getValue() != null) {
            kubectlArgs.addAll(Arrays.asList("--context", contextSupplier.getValue()));
        }
        logger.debug("Executing " + kubectlArgs);
        try {
            return shellExecutor.exec(kubectlArgs, responseMapper);
        } catch (ShellException e) {
            throw new KubectlException(ERROR_MESSAGE_PREFIX, e);
        }
    }

    private String executeKubectl(ContextSupplier contextSupplier, String... args) throws KubectlException {
        return executeKubectlWithResponseMapper(contextSupplier, defaultResponseMapper, args);
    }

    @SuppressWarnings("unchecked")
    List<Pod> getPodsByLabel(String labelName, String labelValue)
            throws KubectlException {
        String selector = labelName + '=' + labelValue;
        if (globalConfiguration.isUseClusterRegistry()) {
            List<String> available = availableClusterRegistryContexts();
            boolean swallow = available.size() > 1;
            List<Pod> collectedPods = new LinkedList<>();

            for (String clusterContext : available) {
                try {
                    List<Pod> clusterPods = getPods(selector, new SimpleContextSupplier(clusterContext));
                    collectedPods.addAll(clusterPods);
                } catch (KubectlException e) {
                    if (swallow) {
                        logger.error("Failed to load pods with Cluster Registry turned on with context:"
                                + clusterContext, e);
                        continue;
                    } else {
                        throw e;
                    }
                }
            }

            return collectedPods;
        } else {
            return getPods(selector, globalSupplier);
        }
    }

    /**
     * We should consider using -o jsonpath to further reduce memory allocation.
     * This would require rework of deserialisation and a change of the model classes
     */
    List<Pod> getPods(String selector, ContextSupplier contextHandler) throws KubectlException {
        Object result = executeKubectlAsObject(contextHandler, "get", "pods", "--selector", selector);
        if (result instanceof KubernetesList) {
            List<HasMetadata> items = ((KubernetesList) result).getItems();
            ArrayList<Pod> pods = new ArrayList<>(items.size());

            for (HasMetadata entity : items) {
                Pod pod = (Pod) entity;
                pod.setAdditionalProperty(PROP_CONTEXT, contextHandler.getValue());
                pods.add(pod);
            }

            return pods;
        } else {
            throw new KubectlException("Unexpected content type");
        }
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
                throw new ClusterRegistryKubectlException("Found no cluster available in cluster registry");
            } else {
                supplier = new SimpleContextSupplier(primary.get(0));
            }
        } else {
            supplier = globalSupplier;
        }
        pod = (Pod) executeKubectlAsObject(supplier, "create", "--validate=false", "-f", podFile.getAbsolutePath());
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
        if (pod.getMetadata().getAnnotations().containsKey(PodCreator.ANN_IAM_REQUEST_NAME)) {
            deleteIamRequest(pod);
        }
    }


    void deletePod(String podName)
            throws InterruptedException, IOException, KubectlException {
        ContextSupplier supplier;
        if (globalConfiguration.isUseClusterRegistry()) {
            availableClusterRegistryContexts().forEach((String t) -> {
                try {
                    executeKubectl(new SimpleContextSupplier(t),
                            "delete", "pod", "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT, podName);
                    deleteIamRequest(new SimpleContextSupplier(t), podName);
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
            deleteIamRequest(supplier, podName);
        }
    }

    void deleteIamRequest(Pod pod) throws KubectlException {
        executeKubectl(new PodContextSupplier(pod),
                "delete", "iam", "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT,
                pod.getMetadata().getAnnotations().get(PodCreator.ANN_IAM_REQUEST_NAME));
    }

    //The problem with only having the String is that we can't tell if the IAMRequest is meant to exist.
    //So we just blindly delete and ignore failures if it can't find the iamRequest
    void deleteIamRequest(ContextSupplier contextSupplier, String podName) throws KubectlException {
        try {
            executeKubectl(contextSupplier, "delete", "iam", "-l", PodCreator.ANN_POD_NAME + "=" + podName,
                    "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT);
        } catch (KubectlException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(ERROR_MESSAGE_PREFIX)) {
                logger.debug("swallowing error because we are executing in multiple clusters", e);
            } else {
                throw e;
            }
        }
    }

    private List<ClusterRegistryItem> getClusters() throws ClusterRegistryKubectlException {
        try {
            return cache.getUnchecked(CACHED_VALUE);
        } catch (UncheckedExecutionException ex) {
            if (ex.getCause() instanceof KubectlException) {
                throw new ClusterRegistryKubectlException(ex.getMessage(), ex.getCause());
            }
            logger.error("unknown failure at loading clusters from registry", ex);
            return Collections.emptyList();
        }
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
                    for (Iterator iterator = array.iterator(); iterator.hasNext(); ) {
                        JsonElement next = (JsonElement) iterator.next();
                        if (next != null
                                && next.isJsonObject()
                                && next.getAsJsonObject().has("kind")
                                && "Cluster".equals(next.getAsJsonObject()
                                .getAsJsonPrimitive("kind").getAsString())) {
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


    private List<String> availableClusterRegistryContexts() throws ClusterRegistryKubectlException {
        Supplier<String> label = () -> globalConfiguration.getClusterRegistryAvailableClusterSelector();
        return registryContexts(label);
    }

    private List<String> registryContexts(Supplier<String> filter)
            throws ClusterRegistryKubectlException {
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

    private List<String> primaryClusterRegistryContexts() throws ClusterRegistryKubectlException {
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

    static class ClusterRegistryKubectlException extends KubectlException {

        public ClusterRegistryKubectlException(String message) {
            super(message);
        }

        public ClusterRegistryKubectlException(String message, Throwable cause) {
            super(message, cause);
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
