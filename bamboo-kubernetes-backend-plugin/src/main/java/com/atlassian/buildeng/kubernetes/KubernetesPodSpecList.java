/*
 * Copyright 2016 - 2022 Atlassian Pty Ltd.
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

import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.sal.api.features.DarkFeatureManager;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class KubernetesPodSpecList {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesPodSpecList.class);
    private final GlobalConfiguration globalConfiguration;
    private final BandanaManager bandanaManager;
    private final DarkFeatureManager darkFeatureManager;

    public KubernetesPodSpecList(
            GlobalConfiguration globalConfiguration,
            BandanaManager bandanaManager,
            DarkFeatureManager darkFeatureManager) {
        this.globalConfiguration = globalConfiguration;
        this.bandanaManager = bandanaManager;
        this.darkFeatureManager = darkFeatureManager;
    }

    public File generate(IsolatedDockerAgentRequest request, String subjectId) throws IOException {
        return createPodFile(createPodSpecList(request, subjectId));
    }

    public void cleanUp(File podFile) {
        this.deletePodFile(podFile);
    }

    private List<Map<String, Object>> createPodSpecList(
            IsolatedDockerAgentRequest request, String subjectId) {
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
            // Temporary Workaround until we fully migrate to IRSA
            removeDefaultRole(finalPod);
            podSpecList.add(finalIamRequest);
        }

        return podSpecList;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplatePod() {
        Yaml yaml = new Yaml(new SafeConstructor());
        return (Map<String, Object>) yaml.load(globalConfiguration.getPodTemplateAsString());
    }

    @VisibleForTesting
    Map<String, Object> addArchitectureOverrides(
            IsolatedDockerAgentRequest request, Map<String, Object> podWithoutArchOverrides) {
        Map<String, Object> archConfig = loadArchitectureConfig();

        if (archConfig.isEmpty()) {
            return podWithoutArchOverrides;
        } else {
            if (request.getConfiguration().isArchitectureDefined()) {
                String architecture = request.getConfiguration().getArchitecture();
                if (archConfig.containsKey(
                        architecture)) { // Architecture matches one in the Kubernetes pod overrides
                    return mergeMap(podWithoutArchOverrides, getSpecificArchConfig(archConfig, architecture));
                } else {
                    String supportedArchs = com.atlassian.buildeng.isolated.docker.GlobalConfiguration
                            .getArchitectureConfigWithBandana(bandanaManager)
                            .keySet()
                            .toString();
                    throw new IllegalArgumentException(
                            "Architecture specified in build configuration was not "
                                    + "found in server's allowed architectures list! Supported architectures are: "
                                    + supportedArchs);
                }
            } else { // Architecture is not specified at all
                return mergeMap(
                        podWithoutArchOverrides,
                        getSpecificArchConfig(archConfig, getDefaultArchitectureName(archConfig)));
            }
        }
    }

    @SuppressWarnings("unchecked")
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

    private File createPodFile(List<Map<String, Object>> podSpecList) throws IOException {
        File f = File.createTempFile("pod", "yaml");
        writeSpecToFile(podSpecList, f);
        return f;
    }

    private void deletePodFile(File podFile) {
        boolean success = true;
        if (podFile != null) {
            try {
                success = podFile.delete();
            } catch (SecurityException e) {
                success = false;
            }
        }
        if (!success) {
            logger.warn("Failed to delete podSpec file after request: {}", podFile.getAbsolutePath());
        }
    }

    // A hacky way to remove a default role being provided by kube2iam
    // Will remove once we fully migrate to IRSA
    @SuppressWarnings("unchecked")
    private void removeDefaultRole(Map<String, Object> finalPod) {
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
        overrides.forEach(
                (String t, Object u) -> {
                    Object originalEntry = merged.get(t);
                    if (originalEntry instanceof Map && u instanceof Map) {
                        merged.put(t, mergeMap((Map<String, Object>) originalEntry, (Map<String, Object>) u));
                    } else if (originalEntry instanceof Collection && u instanceof Collection) {
                        ArrayList<Map<String, Object>> lst = new ArrayList<>();

                        if (t.equals("containers")) {
                            mergeById(
                                    "name",
                                    lst,
                                    (Collection<Map<String, Object>>) originalEntry,
                                    (Collection<Map<String, Object>>) u);
                        } else if (t.equals("hostAliases")) {
                            mergeById(
                                    "ip",
                                    lst,
                                    (Collection<Map<String, Object>>) originalEntry,
                                    (Collection<Map<String, Object>>) u);
                        } else {
                            lst.addAll((Collection<Map<String, Object>>) originalEntry);
                            lst.addAll((Collection<Map<String, Object>>) u);
                        }
                        merged.put(t, lst);
                    } else {
                        merged.put(t, u);
                    }
                });
        return merged;
    }

    private static void mergeById(
            String id,
            ArrayList<Map<String, Object>> lst,
            Collection<Map<String, Object>> originalEntry,
            Collection<Map<String, Object>> u) {
        Map<String, Map<String, Object>> containers1 = originalEntry.stream()
                .collect(Collectors.toMap(x -> (String) x.get(id), x -> x));
        Map<String, Map<String, Object>> containers2 = u.stream()
                .collect(Collectors.toMap(x -> (String) x.get(id), x -> x));

        containers1.forEach(
                (String name, Map<String, Object> container1) -> {
                    Map<String, Object> container2 = containers2.remove(name);
                    if (container2 != null) {
                        lst.add(mergeMap(container1, container2));
                    } else {
                        lst.add(container1);
                    }
                });
        lst.addAll(containers2.values());
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    Map<String, Object> getSpecificArchConfig(Map<String, Object> archConfig, String s) {
        return (Map<String, Object>) ((Map<String, Object>) archConfig.get(s)).get("config");
    }

    @VisibleForTesting
    String getDefaultArchitectureName(Map<String, Object> archConfig) {
        return (String) archConfig.get(DEFAULT_ARCHITECTURE);
    }

}
