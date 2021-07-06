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

import com.atlassian.buildeng.kubernetes.cluster.ClusterFactory;
import com.atlassian.buildeng.kubernetes.cluster.ClusterRegistryItem;
import com.atlassian.buildeng.kubernetes.context.ContextSupplier;
import com.atlassian.buildeng.kubernetes.context.GlobalContextSupplier;
import com.atlassian.buildeng.kubernetes.context.PodContextSupplier;
import com.atlassian.buildeng.kubernetes.context.SimpleContextSupplier;
import com.atlassian.buildeng.kubernetes.exception.ClusterRegistryKubectlException;
import com.atlassian.buildeng.kubernetes.exception.KubectlException;
import com.atlassian.buildeng.kubernetes.exception.KubernetesExceptionParser;
import com.atlassian.buildeng.kubernetes.serialization.JsonResponseMapper;
import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;
import com.atlassian.buildeng.kubernetes.serialization.StringResponseMapper;
import com.atlassian.buildeng.kubernetes.shell.ShellException;
import com.atlassian.buildeng.kubernetes.shell.ShellExecutor;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KubernetesClient {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesClient.class);
    private static final String ERROR_MESSAGE_PREFIX = "kubectl returned non-zero exit code.";

    private final ClusterFactory clusterFactory;
    private final ContextSupplier globalContextSupplier;
    private final ShellExecutor shellExecutor;
    private final GlobalConfiguration globalConfiguration;
    private final StringResponseMapper defaultResponseMapper = new StringResponseMapper();
    private final JsonResponseMapper jsonResponseMapper = new JsonResponseMapper();
    private final KubernetesExceptionParser kubernetesExceptionParser = new KubernetesExceptionParser();

    private final DeletePodLogger deletePodLogger = new DeletePodLogger();

    KubernetesClient(GlobalConfiguration globalConfiguration, ShellExecutor shellExecutor) {
        this.globalConfiguration = globalConfiguration;
        this.shellExecutor = shellExecutor;

        globalContextSupplier = new GlobalContextSupplier(globalConfiguration);
        clusterFactory = new ClusterFactory(this, globalContextSupplier);
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
            logger.debug("mapping shell exception");
            throw kubernetesExceptionParser.map(ERROR_MESSAGE_PREFIX, e);
        }
    }

    public String executeKubectl(ContextSupplier contextSupplier, String... args) throws KubectlException {
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
            return getPods(selector, globalContextSupplier);
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
                pod.setAdditionalProperty(Const.PROP_CONTEXT, contextHandler.getValue());
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
            logger.debug("loading primary cluster contexts");
            List<String> primary = primaryClusterRegistryContexts();
            if (primary.isEmpty()) {
                logger.debug("loading available contexts");
                primary = availableClusterRegistryContexts();
            }
            Collections.shuffle(primary);
            if (primary.isEmpty()) {
                logger.debug("Found no cluster available in cluster registry");
                throw new ClusterRegistryKubectlException("Found no cluster available in cluster registry");
            } else {
                logger.debug("using {} context", primary.get(0));
                supplier = new SimpleContextSupplier(primary.get(0));
            }
        } else {
            supplier = globalContextSupplier;
        }
        try {
            pod = (Pod) executeKubectlAsObject(supplier, "create", "--validate=false", "-f", podFile.getAbsolutePath());
        } catch (KubectlException e) {
            try {
                String body = FileUtils.readFileToString(podFile, Charsets.UTF_8);
                logger.error("Invalid kubectl request. File at fault: \n" + body, e);
            } catch (IOException ioException) {
                //We don't log the file content in case of error
            }
            throw e;
        }
        pod.setAdditionalProperty(Const.PROP_CONTEXT, globalContextSupplier.getValue());
        return pod;
    }

    String describePod(Pod pod)
            throws KubectlException {
        return executeKubectl(new PodContextSupplier(pod), "describe", "pod", KubernetesHelper.getName(pod));
    }

    void deletePod(Pod pod)
            throws KubectlException {
        long startTime = System.currentTimeMillis();
        executeKubectl(new PodContextSupplier(pod),
                "delete", "pod", "--grace-period=0 --force", "--timeout=" + Constants.KUBECTL_DELETE_TIMEOUT,
                        KubernetesHelper.getName(pod));
        long podDeletionEnd = System.currentTimeMillis();
        deletePodLogger.log(String.format("pod deletion took %f ms", podDeletionEnd - startTime));
        if (pod.getMetadata().getAnnotations().containsKey(PodCreator.ANN_IAM_REQUEST_NAME)) {
            deleteIamRequest(pod);
        }
        long endTime = System.currentTimeMillis();
        deletePodLogger.log(String.format("iam deletion took %f ms", endTime - podDeletionEnd));
        deletePodLogger.log(String.format("total deletion time %f ms", endTime - startTime));
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
            supplier = globalContextSupplier;
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

    private List<String> availableClusterRegistryContexts() throws ClusterRegistryKubectlException {
        Supplier<String> label = () -> globalConfiguration.getClusterRegistryAvailableClusterSelector();
        return registryContexts(label);
    }

    private List<String> registryContexts(Supplier<String> filter)
            throws ClusterRegistryKubectlException {
        List<ClusterRegistryItem> clusters = clusterFactory.getClusters();
        return clusters.stream()
                .filter((ClusterRegistryItem t) -> t.getLabels().stream()
                        .anyMatch((Pair<String, String> t1) -> StringUtils.equals(t1.getKey(), filter.get())))
                .map((ClusterRegistryItem t) ->
                        t.getLabels().stream()
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

}
