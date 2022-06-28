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

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.kubernetes.exception.KubectlException;
import com.atlassian.buildeng.kubernetes.exception.PodLimitQuotaExceededException;
import com.atlassian.buildeng.kubernetes.jmx.KubeJmxService;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@ExtendWith({MockitoExtension.class})
public class KubernetesIsolatedDockerImplTest {
    @Mock
    GlobalConfiguration globalConfiguration;
    @Mock
    KubeJmxService kubeJmxService;
    @Mock
    ExecutorService executor;
    @Mock
    SubjectIdService subjectIdService;
    @Mock
    KubernetesClient kubernetesClient;
    @Mock
    BandanaManager bandanaManager;


    @InjectMocks
    KubernetesIsolatedDockerImpl kubernetesIsolatedDocker;

    @Test
    @SuppressWarnings("unchecked")
    public void testContainersMergedByName() {
        Yaml yaml =  new Yaml(new SafeConstructor());
        String ts = "apiVersion: v1\n"
              + "kind: Pod\n"
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
        String os =
                "metadata:\n"
              + "    namespace: buildeng\n"
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

        Map<String, Object> template = (Map<String, Object>) yaml.load(ts);
        Map<String, Object> overrides = (Map<String, Object>) yaml.load(os);
        Map<String, Object> merged = KubernetesIsolatedDockerImpl.mergeMap(template, overrides);
        Map<String, Object> spec = (Map<String, Object>) merged.get("spec");
        assertEquals(3, ((Collection) spec.get("containers")).size());
        assertEquals(2, ((Collection) spec.get("volumes")).size());
        assertEquals(3, ((Collection) spec.get("hostAliases")).size());

        List<Map<String, Object>> containers = ((List<Map<String, Object>>) spec.get("containers"));
        assertEquals(1, containers.stream().filter(c -> c.containsValue("main")).collect(toList()).size());
        for (Map<String, Object> container : containers) {
            if (container.containsValue("main")) {
                assertNotEquals(null, container.get("image"));
                assertNotEquals(null, container.get("volumeMounts"));
            }
        }
        List<Map<String, Object>> hostAliases = ((List<Map<String, Object>>) spec.get("hostAliases"));
        assertEquals(2, hostAliases.stream()
                .filter((Map<String, Object> t) -> "127.0.0.1".equals(t.get("ip")))
                .flatMap((Map<String, Object> t) -> ((List<String>) t.get("hostnames")).stream())
                .count());
        
    }

    @Test
    public void pbcShouldRetryOnExceedingQuota() {
        KubectlException ke = new PodLimitQuotaExceededException("exceeded quota");
        final AtomicBoolean retry = new AtomicBoolean(false);
        IsolatedDockerRequestCallback callback = new IsolatedDockerRequestCallback() {
            @Override
            public void handle(IsolatedDockerAgentResult result) {
                retry.set(result.isRetryRecoverable());
            }

            @Override
            public void handle(IsolatedDockerAgentException exception) {

            }
        };
        kubernetesIsolatedDocker.handleKubeCtlException(callback, ke);
        assertTrue(retry.get(), "PBC should retry on exceeding kube quota");
    }

    @Test
    public void testSubjectIdForPlan() {
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(null,
            "TEST-PLAN-JOB1",
            UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
            0, "bk", 0, true);

        when(subjectIdService.getSubjectId(any(PlanKey.class))).thenReturn("mock-subject-id");

        kubernetesIsolatedDocker.getSubjectId(request);
        verify(subjectIdService).getSubjectId(PlanKeys.getPlanKey("TEST-PLAN-JOB1"));
    }

    @Test
    public void testSubjectIdForDeployment() {
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(null,
            "111-222-333",
            UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
            0, "bk", 0, false);

        when(subjectIdService.getSubjectId(any(Long.class))).thenReturn("mock-subject-id");
        kubernetesIsolatedDocker.getSubjectId(request);
        verify(subjectIdService).getSubjectId(111L);
    }

    @Test
    public void testPodSpecIsUnmodifiedIfArchitectureConfigIsEmpty() {
        Configuration c = ConfigurationBuilder.create("docker-image").build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(c,
                "TEST-PLAN-JOB1",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
                0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn("");

        Map<String, Object> returnedMap = kubernetesIsolatedDocker.addArchitectureOverrides(request, new HashMap<>());

        assertEquals(new HashMap<>(), returnedMap);
    }

    @Test
    public void testArchitectureOverrideIsFetchedCorrectly() throws IOException {
        Map<String, Object> config = kubernetesIsolatedDocker.getSpecificArchConfig(getArchitecturePodOverridesAsYaml(), "arm64");
        assertEquals(Collections.singletonMap("foo", "bar"), config);
    }

    @Test
    public void testPodSpecHasOverrideIfArchitectureOmittedButServerHasDefaultDefined() throws IOException {
        Configuration c = ConfigurationBuilder.create("docker-image").build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(c,
                "TEST-PLAN-JOB1",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
                0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn(getArchitecturePodOverridesAsString());

        Map<String, Object> returnedMap = kubernetesIsolatedDocker.addArchitectureOverrides(request, new HashMap<>());

        assertEquals(Collections.singletonMap("foo", "bar"), returnedMap);
    }

    @Test
    public void testPodSpecHasOverrideAddedIfArchitectureIsManuallySpecifiedAndExists() throws IOException {
        Configuration c = ConfigurationBuilder.create("docker-image").withArchitecture("arm64").build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(c,
                "TEST-PLAN-JOB1",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
                0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn(getArchitecturePodOverridesAsString());

        Map<String, Object> returnedMap = kubernetesIsolatedDocker.addArchitectureOverrides(request, new HashMap<>());

        assertEquals(Collections.singletonMap("foo", "bar"), returnedMap);
    }

    @Test
    public void testPodSpecHasOverrideAddedIfArchitectureIsManuallySpecifiedAndDoesNotExist() throws IOException {
        Configuration c = ConfigurationBuilder.create("docker-image").withArchitecture("fakeArch").build();
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(c,
                "TEST-PLAN-JOB1",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
                0, "bk", 0, true);

        when(globalConfiguration.getBandanaArchitecturePodConfig()).thenReturn(getArchitecturePodOverridesAsString());
        when(bandanaManager.getValue(any(), matches("com.atlassian.buildeng.pbc.architecture.config.parsed")))
                .thenReturn(new LinkedHashMap<>(Collections.singletonMap("myArch", "")));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> kubernetesIsolatedDocker.addArchitectureOverrides(request, new HashMap<>()));
        assertEquals("Architecture specified in build configuration was not found in server's allowed architectures list! Supported architectures are: [myArch]",
                exception.getMessage());
    }

    @Test
    public void testLoadAllowlist() {
        when(globalConfiguration.getArtifactoryCacheAllowlistAsString()).thenReturn(
                "- test123\n" +
                "- test456");

        HashSet<String> allowList = new HashSet<>();
        allowList.add("test123");
        allowList.add("test456");

        assertEquals(kubernetesIsolatedDocker.loadAllowlist(), allowList);
    }

    @Test
    public void testAddCachePodSpec() {
        Yaml yaml =  new Yaml(new SafeConstructor());

        String ogSpec = "apiVersion: v1\n"
                + "kind: Pod\n"
                + "spec:\n" +
                "  volumes:\n" +
                "    - name: git-cache\n" +
                "      flexVolume:\n" +
                "        driver: mkleint/cow\n" +
                "        fsType: cow\n" +
                "        options:\n" +
                "          lower: /var/per-build-cache/gitcache\n" +
                "  containers:\n" +
                "    volumeMounts:\n" +
                "      - name: git-cache\n" +
                "        mountPath: /pbc/overlay/gitcache";
        String expectedSpec = "apiVersion: v1\n"
                + "kind: Pod\n"
                + "spec:\n" +
                "  volumes:\n" +
                "    - name: git-cache\n" +
                "      flexVolume:\n" +
                "        driver: mkleint/cow\n" +
                "        fsType: cow\n" +
                "        options:\n" +
                "          lower: /var/per-build-cache/gitcache\n" +
                "    - name: m2-cache\n" +
                "      flexVolume:\n" +
                "        driver: mkleint/cow\n" +
                "        fsType: cow\n" +
                "        options:\n" +
                "          lower: /var/per-build-cache/m2cache\n" +
                "  containers:\n" +
                "    volumeMounts:\n" +
                "      - name: git-cache\n" +
                "        mountPath: /pbc/overlay/gitcache\n" +
                "      - name: m2-cache\n" +
                "        mountPath: /pbc/overlay/m2cache";
        when(globalConfiguration.getArtifactoryCachePodSpecAsString()).thenReturn("spec:\n" +
                "  volumes:\n" +
                "    - name: m2-cache\n" +
                "      flexVolume:\n" +
                "        driver: mkleint/cow\n" +
                "        fsType: cow\n" +
                "        options:\n" +
                "          lower: /var/per-build-cache/m2cache\n" +
                "  containers:\n" +
                "    volumeMounts:\n" +
                "      - name: m2-cache\n" +
                "        mountPath: /pbc/overlay/m2cache");

        Map<String, Object> ogYaml = (Map<String, Object>) yaml.load(ogSpec);
        Map<String, Object> newPodSpec = kubernetesIsolatedDocker.addCachePodSpec(ogYaml);
        Map<String, Object> expected = (Map<String, Object>) yaml.load(expectedSpec);

        assertEquals(newPodSpec, expected);
    }

    // Helper functions

    private String getArchitecturePodOverridesAsString() throws IOException {
        return FileUtils.readFileToString(new File(this.getClass().getResource("/architecturePodOverrides.yaml").getFile()), "UTF-8");
    }

    private Map<String, Object> getArchitecturePodOverridesAsYaml() throws IOException {
        Yaml yamlReader = new Yaml(new SafeConstructor());
        Map<String, Object> yaml = (Map<String, Object>) yamlReader.load(getArchitecturePodOverridesAsString());
        return yaml;
    }
}
