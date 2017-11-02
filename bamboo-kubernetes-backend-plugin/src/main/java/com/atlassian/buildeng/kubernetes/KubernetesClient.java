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
        List<String> kubectlArgs = new ArrayList<>(Arrays.asList(args));
        kubectlArgs.addAll(Arrays.asList("-o", "json"));
        return KubernetesHelper.loadJson(executeKubectl(kubectlArgs.toArray(new String[0])));
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
        // --show-all displays "Completed" status pods as well
        return ((KubernetesList) executeKubectlAsJson("get", "pods", "--selector", label, "--show-all"))
                .getItems().stream().map((HasMetadata pod) -> (Pod) pod).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    Pod createPod(File podFile) throws InterruptedException, IOException, KubectlException {
        return (Pod) executeKubectlAsJson("create", "-f", podFile.getAbsolutePath());
    }

    String describePod(Pod pod)
            throws InterruptedException, IOException, KubectlException {
        return executeKubectl("describe", "pod", KubernetesHelper.getName(pod));
    }

    boolean deletePod(Pod pod) {
        try {
            executeKubectl("delete", "pod", KubernetesHelper.getName(pod));
        } catch (Exception e) {
            logger.error("Failed to delete pod with name: " + KubernetesHelper.getName(pod) + e);
            return false;
        }
        return true;
    }

    class KubectlException extends Exception {
        KubectlException(String message) {
            super(message);
        }
    }
}
