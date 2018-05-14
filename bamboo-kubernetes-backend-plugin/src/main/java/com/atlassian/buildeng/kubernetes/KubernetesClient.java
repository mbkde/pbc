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

import com.google.common.collect.Lists;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Pod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KubernetesClient {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesClient.class);

    private final GlobalConfiguration globalConfiguration;

    KubernetesClient(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }

    private Object executeKubectlAsJson(String... args)
            throws InterruptedException, IOException, KubectlException {
        return KubernetesHelper.loadJson(executeKubectl(Lists.asList("-o", "json", args).toArray(new String[0])));
    }

    private String executeKubectl(String... args)
            throws InterruptedException, IOException, KubectlException {
        List<String> kubectlArgs = new ArrayList<>(Arrays.asList(args));
        kubectlArgs.add(0, Constants.KUBECTL_EXECUTABLE);
        if (globalConfiguration.getCurrentContext() != null) {
            kubectlArgs.addAll(Arrays.asList("--context", globalConfiguration.getCurrentContext()));
        }

        ProcessBuilder pb = new ProcessBuilder(kubectlArgs);
        pb.redirectErrorStream(true);
        // kubectl requires HOME env to find the config, but the Bamboo server JVM might not have it setup.
        pb.environment().put("HOME", System.getProperty("user.home"));
        Process process = pb.start();
        String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);

        int ret = process.waitFor();
        if (ret != 0) {
            throw new KubectlException("kubectl returned non-zero exit code. Output: " + output);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    List<Pod> getPods(String labelName, String labelValue)
            throws InterruptedException, IOException, KubectlException {
        String label = labelName + '=' + labelValue;
        return ((KubernetesList) executeKubectlAsJson("get", "pods", "--selector", label))
                .getItems().stream().map((HasMetadata pod) -> (Pod) pod).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    Pod createPod(File podFile)
            throws InterruptedException, IOException, KubectlException {
        return (Pod) executeKubectlAsJson("create", "--validate=false", "-f", podFile.getAbsolutePath());
    }

    String describePod(Pod pod)
            throws InterruptedException, IOException, KubectlException {
        return executeKubectl("describe", "pod", KubernetesHelper.getName(pod));
    }

    void deletePod(Pod pod)
            throws InterruptedException, IOException, KubectlException {
        deletePod(KubernetesHelper.getName(pod));
    }

    
    void deletePod(String podName)
            throws InterruptedException, IOException, KubectlException {
        executeKubectl("delete", "pod", podName);
    }
    
    class KubectlException extends Exception {
        KubectlException(String message) {
            super(message);
        }
    }
}
