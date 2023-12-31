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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.sal.api.features.DarkFeatureManager;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@ExtendWith({MockitoExtension.class})
public class KubernetesPodSpecListTest {
    public KubernetesPodSpecListTest() {}

    @Mock
    GlobalConfiguration globalConfiguration;

    @Mock
    BandanaManager bandanaManager;

    @Mock
    DarkFeatureManager darkFeatureManager;

    @Mock
    PodCreator podCreator;

    @InjectMocks
    KubernetesPodSpecList kubernetesPodSpecList;

    @Test
    @SuppressWarnings("unchecked")
    void testContainersMergedByName() {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        String templateString = getPodTemplateAsString();
        String overridesString = "metadata:\n" + "    namespace: buildeng\n"
                + "    annotations:\n"
                + "        iam.amazonaws.com/role: arn:aws:iam::123456678912:role/staging-bamboo\n"
                + "spec:\n"
                + "  hostAliases:\n"
                + "    - ip: 100.100.0.1\n"
                + "      hostnames:\n"
                + "          - remote\n"
                + "    - ip: 127.0.0.1\n"
                + "      hostnames:\n"
                + "          - bamboo-agent\n"
                + "  containers:\n"
                + "    - name: main\n"
                + "      image: xueshanf/awscli:latest\n"
                + "    - name: myContainer3\n"
                + "      image: xueshanf/awscli:latest\n"
                + "  volumes:\n"
                + "    - name: myvolume\n";

        Map<String, Object> template = (Map<String, Object>) yaml.load(templateString);
        Map<String, Object> overrides = (Map<String, Object>) yaml.load(overridesString);
        Map<String, Object> merged = KubernetesPodSpecList.mergeMap(template, overrides);
        Map<String, Object> spec = (Map<String, Object>) merged.get("spec");
        assertEquals(3, ((Collection<Map<String, Object>>) spec.get("containers")).size());
        assertEquals(2, ((Collection<Map<String, Object>>) spec.get("volumes")).size());
        assertEquals(3, ((Collection<Map<String, Object>>) spec.get("hostAliases")).size());

        List<Map<String, Object>> containers = ((List<Map<String, Object>>) spec.get("containers"));
        assertEquals(1, (int)
                containers.stream().filter(c -> c.containsValue("main")).count());
        for (Map<String, Object> container : containers) {
            if (container.containsValue("main")) {
                assertNotEquals(null, container.get("image"));
                assertNotEquals(null, container.get("volumeMounts"));
            }
        }
        List<Map<String, Object>> hostAliases = ((List<Map<String, Object>>) spec.get("hostAliases"));
        assertEquals(
                2,
                hostAliases.stream()
                        .filter((Map<String, Object> t) -> "127.0.0.1".equals(t.get("ip")))
                        .mapToLong((Map<String, Object> t) -> ((List<String>) t.get("hostnames")).size())
                        .sum());
    }

    @Test
    void testPodSpecIsUnmodifiedIfArchitectureConfigIsEmpty() {
        Configuration c = ConfigurationBuilder.create("docker-image").build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(
                c, "TEST-PLAN-JOB1", UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn("");

        Map<String, Object> returnedMap = kubernetesPodSpecList.addArchitectureOverrides(request, new HashMap<>());

        assertEquals(new HashMap<>(), returnedMap);
    }

    @Test
    void testArchitectureOverrideIsFetchedCorrectly() throws IOException {
        Map<String, Object> config =
                kubernetesPodSpecList.getSpecificArchConfig(getArchitecturePodOverridesAsYaml(), "arm64");
        assertEquals(Collections.singletonMap("foo", "bar"), config);
    }

    @Test
    void testPodSpecHasOverrideIfArchitectureOmittedButServerHasDefaultDefined() throws IOException {
        Configuration c = ConfigurationBuilder.create("docker-image").build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(
                c, "TEST-PLAN-JOB1", UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn(getArchitecturePodOverridesAsString());

        Map<String, Object> returnedMap = kubernetesPodSpecList.addArchitectureOverrides(request, new HashMap<>());

        assertEquals(Collections.singletonMap("foo", "bar"), returnedMap);
    }

    @Test
    void testPodSpecHasOverrideAddedIfArchitectureIsManuallySpecifiedAndExists() throws IOException {
        Configuration c = ConfigurationBuilder.create("docker-image")
                .withArchitecture("arm64")
                .build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(
                c, "TEST-PLAN-JOB1", UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn(getArchitecturePodOverridesAsString());

        Map<String, Object> returnedMap = kubernetesPodSpecList.addArchitectureOverrides(request, new HashMap<>());

        assertEquals(Collections.singletonMap("foo", "bar"), returnedMap);
    }

    @Test
    void testPodSpecHasOverrideAddedIfArchitectureIsManuallySpecifiedAndDoesNotExist() throws IOException {
        Configuration c = ConfigurationBuilder.create("docker-image")
                .withArchitecture("fakeArch")
                .build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(
                c, "TEST-PLAN-JOB1", UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn(getArchitecturePodOverridesAsString());
        when(bandanaManager.getValue(any(), matches("com.atlassian.buildeng.pbc.architecture.config.parsed")))
                .thenReturn(new LinkedHashMap<>(Collections.singletonMap("myArch", "")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> kubernetesPodSpecList.addArchitectureOverrides(request, new HashMap<>()));
        assertEquals(
                "Architecture specified in build configuration was not found in server's allowed"
                        + " architectures list! Supported architectures are: [myArch]",
                exception.getMessage());
    }

    @Test
    void testGenerate() throws IOException {
        // given
        final IsolatedDockerAgentRequest request = mockCallsFromCreate();
        final String stringId = "abc123";
        final File file = mock(File.class);
        when(globalConfiguration.getArtifactoryCacheAllowListAsString()).thenReturn("");
        when(podCreator.create(request)).thenReturn(Collections.emptyMap());

        try (MockedStatic<File> mockFile = mockStatic(File.class)) {
            try (MockedStatic<FileUtils> mockFileUtils = mockStatic(FileUtils.class)) {
                mockFile.when(() -> File.createTempFile("pod", "yaml")).thenReturn(file);
                // when
                kubernetesPodSpecList.generate(request, stringId);
                // then pod spec list generated and sent to file
                assertPodSpecFileCreated(mockFile, mockFileUtils, file);
                verify(globalConfiguration, never()).getArtifactoryCachePodSpecAsString();
            }
        }
    }

    @Test
    void testCleanUp() {
        // given
        final File file = mock(File.class);
        when(file.delete()).thenReturn(true);
        // when
        kubernetesPodSpecList.cleanUp(file);
        // then
        verify(file).delete();
    }

    @Test
    public void testCleanUpNull() {
        // No exception when a null file is used
        kubernetesPodSpecList.cleanUp(null);
    }

    @Test
    void testCleanUpException() {
        // delete exceptions are caught and squashed
        // given
        final File file = mock(File.class);
        when(file.delete()).thenThrow(new SecurityException());
        // when
        kubernetesPodSpecList.cleanUp(file);
        // then
        verify(file).delete();
    }

    @Test
    void testCleanUpFail() {
        // delete fail result is caught and squashed
        // given
        final File file = mock(File.class);
        when(file.delete()).thenReturn(false);
        // when
        kubernetesPodSpecList.cleanUp(file);
        // then
        verify(file).delete();
    }

    @Test
    void testArtifactoryCacheSpecAddedWhenPlanInAllowList() throws IOException {
        // given
        final IsolatedDockerAgentRequest request = mockCallsFromCreate(false);
        final String stringId = "abc123";
        final File file = mock(File.class);
        when(globalConfiguration.getArtifactoryCacheAllowListAsString()).thenReturn("- PLAN-KEY");
        when(podCreator.create(request)).thenReturn(Collections.emptyMap());

        mockArtifactoryCache(globalConfiguration);
        try (MockedStatic<File> mockFile = mockStatic(File.class)) {
            try (MockedStatic<FileUtils> mockFileUtils = mockStatic(FileUtils.class)) {
                mockFile.when(() -> File.createTempFile("pod", "yaml")).thenReturn(file);
                // when
                kubernetesPodSpecList.generate(request, stringId);
                // then pod spec list generated and sent to file
                assertPodSpecFileCreated(mockFile, mockFileUtils, file);
                verify(globalConfiguration).getArtifactoryCachePodSpecAsString();
            }
        }
    }

    @Test
    void testArtifactoryCacheWhenConfiguredLocally() throws IOException {
        // given
        final IsolatedDockerAgentRequest request = mockCallsFromCreate(true);
        final String stringId = "abc123";
        final File file = mock(File.class);
        when(podCreator.create(request)).thenReturn(Collections.emptyMap());

        mockArtifactoryCache(globalConfiguration);
        try (MockedStatic<File> mockFile = mockStatic(File.class)) {
            try (MockedStatic<FileUtils> mockFileUtils = mockStatic(FileUtils.class)) {
                mockFile.when(() -> File.createTempFile("pod", "yaml")).thenReturn(file);
                // when
                kubernetesPodSpecList.generate(request, stringId);
                // then pod spec list generated and sent to file
                assertPodSpecFileCreated(mockFile, mockFileUtils, file);
                verify(globalConfiguration).getArtifactoryCachePodSpecAsString();
            }
        }
    }

    @Test
    void testAddCachePodSpec() {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

        String ogSpec = "apiVersion: v1\n" + "kind: Pod\n"
                + "spec:\n"
                + "  volumes:\n"
                + "    - name: git-cache\n"
                + "      flexVolume:\n"
                + "        driver: mkleint/cow\n"
                + "        fsType: cow\n"
                + "        options:\n"
                + "          lower: /var/per-build-cache/gitcache\n"
                + "  containers:\n"
                + "    volumeMounts:\n"
                + "      - name: git-cache\n"
                + "        mountPath: /pbc/overlay/gitcache";
        String expectedSpec = "apiVersion: v1\n" + "kind: Pod\n"
                + "spec:\n"
                + "  volumes:\n"
                + "    - name: git-cache\n"
                + "      flexVolume:\n"
                + "        driver: mkleint/cow\n"
                + "        fsType: cow\n"
                + "        options:\n"
                + "          lower: /var/per-build-cache/gitcache\n"
                + "    - name: m2-cache\n"
                + "      flexVolume:\n"
                + "        driver: mkleint/cow\n"
                + "        fsType: cow\n"
                + "        options:\n"
                + "          lower: /var/per-build-cache/m2cache\n"
                + "  containers:\n"
                + "    volumeMounts:\n"
                + "      - name: git-cache\n"
                + "        mountPath: /pbc/overlay/gitcache\n"
                + "      - name: m2-cache\n"
                + "        mountPath: /pbc/overlay/m2cache";

        mockArtifactoryCache(globalConfiguration);

        Map<String, Object> ogYaml = (Map<String, Object>) yaml.load(ogSpec);
        Map<String, Object> newPodSpec = kubernetesPodSpecList.addCachePodSpec(ogYaml);
        Map<String, Object> expected = (Map<String, Object>) yaml.load(expectedSpec);

        assertEquals(newPodSpec, expected);
    }

    @Test
    void testLoadAllowList() {
        when(globalConfiguration.getArtifactoryCacheAllowListAsString()).thenReturn("- test123\n" + "- test456");

        HashSet<String> allowList = new HashSet<>();
        allowList.add("test123");
        allowList.add("test456");

        assertEquals(kubernetesPodSpecList.loadAllowList(), allowList);
    }

    // Helper functions
    private String getPodTemplateAsString() {
        return "apiVersion: v1\n" + "kind: Pod\n"
                + "metadata:\n"
                + "  name: aws-cli\n"
                + "spec:\n"
                + "  hostAliases:\n"
                + "    - ip: 192.168.1.1\n"
                + "      hostnames:\n"
                + "          - wifi\n"
                + "    - ip: 127.0.0.1\n"
                + "      hostnames:\n"
                + "          - me.local\n"
                + "  containers:\n"
                + "    - name: main\n"
                + "      volumeMounts:\n"
                + "          - name: secrets\n"
                + "            mountPath: /root/.aws\n"
                + "    - name: myContainer2\n"
                + "      image: xueshanf/awscli:latest\n"
                + "  restartPolicy: Never\n"
                + "  volumes:\n"
                + "    - name: secrets\n"
                + "      secret:\n"
                + "        secretName: bitbucket-bamboo\n";
    }

    private String getArchitecturePodOverridesAsString() throws IOException {
        return FileUtils.readFileToString(
                new File(Objects.requireNonNull(this.getClass().getResource("/architecturePodOverrides.yaml"))
                        .getFile()),
                "UTF-8");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertStringSpecToYaml(String spec) {
        Yaml yamlReader = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map<String, Object>) yamlReader.load(spec);
    }

    private Map<String, Object> getArchitecturePodOverridesAsYaml() throws IOException {
        return convertStringSpecToYaml(getArchitecturePodOverridesAsString());
    }

    private IsolatedDockerAgentRequest mockCallsFromCreate() {
        return mockCallsFromCreate(false);
    }

    private IsolatedDockerAgentRequest mockCallsFromCreate(boolean useArtifactoryCache) {
        final IsolatedDockerAgentRequest request = mock(IsolatedDockerAgentRequest.class);
        Configuration requestConfiguration = mock(Configuration.class);
        HashSet<String> featureFlags = new HashSet<>();
        if (useArtifactoryCache) {
            featureFlags.add("ARTIFACTORY_CACHE");
        }
        when(requestConfiguration.getFeatureFlags()).thenReturn(featureFlags);
        when(globalConfiguration.getPodTemplateAsString()).thenReturn(getPodTemplateAsString());
        when(request.getConfiguration()).thenReturn(requestConfiguration);
        if (!useArtifactoryCache) {
            when(request.getResultKey()).thenReturn("PLAN-KEY");
        }
        when(requestConfiguration.isAwsRoleDefined()).thenReturn(false);
        return request;
    }

    private void assertPodSpecFileCreated(
            MockedStatic<File> mockFile, MockedStatic<FileUtils> mockFileUtils, File file) {
        mockFile.verify(() -> File.createTempFile("pod", "yaml"));
        mockFileUtils.verify(() -> FileUtils.write(
                eq(file),
                any(), // Any way to improve this?
                matches("UTF-8"),
                eq(false)));
    }

    private void mockArtifactoryCache(GlobalConfiguration globalConfiguration) {
        when(globalConfiguration.getArtifactoryCachePodSpecAsString())
                .thenReturn("spec:\n" + "  volumes:\n"
                        + "    - name: m2-cache\n"
                        + "      flexVolume:\n"
                        + "        driver: mkleint/cow\n"
                        + "        fsType: cow\n"
                        + "        options:\n"
                        + "          lower: /var/per-build-cache/m2cache\n"
                        + "  containers:\n"
                        + "    volumeMounts:\n"
                        + "      - name: m2-cache\n"
                        + "        mountPath: /pbc/overlay/m2cache");
    }
}
