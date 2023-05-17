/*
 * Copyright 2018 Atlassian Pty Ltd.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.sal.api.features.DarkFeatureManager;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@ExtendWith(MockitoExtension.class)
class PodCreatorTest {

    private GlobalConfiguration globalConfiguration;
    private DarkFeatureManager darkFeatureManager;
    private PodCreator podCreator;
    private Yaml yaml;
    private UUID requestUuid;
    private String resultKey;

    private static final String EXPECTED_POD_NAME =
            "shardspipeline-servicedeskembeddablesservicede-455-379ad7b0-b4f5-4fae-914b-070e9442c0a9";
    private static final String EXPECTED_IAM_REQUEST_NAME =
            "shardspipeline-servicedeskembeddabl-455-iamrequest-379ad7b0-b4f5-4fae-914b-070e9442c0a9";
    private static final String EXPECTED_IRSA_NAME = "iamtoken-379ad7b0-b4f5-4fae-914b-070e9442c0a9";
    private static final String BAMBOO_BASE_URL = "http://test-bamboo.com";
    private static final String SIDEKICK_IMAGE = "sidekickImage";
    private static final String EXPECTED_ANNOTATION_RESULTID =
            "shardspipeline-servicedeskembeddablesservicedeskembeddab...Z";
    private static final String IMAGE_NAME = "testImage";

    public PodCreatorTest() {}

    @BeforeEach
    public void setUp() {

        globalConfiguration = mock(GlobalConfiguration.class);
        darkFeatureManager = mock(DarkFeatureManager.class);
        podCreator = new PodCreator(globalConfiguration, darkFeatureManager);
        requestUuid = UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9");
        resultKey = "shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp-455";
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setCanonical(false);
        yaml = new Yaml(options);
    }

    @Test
    void testCreatePodName() {
        String result = podCreator.createPodName(
                new IsolatedDockerAgentRequest(null, resultKey, requestUuid, 0, "bk", 0, true, "someRandomToken"));
        assertEquals(EXPECTED_POD_NAME, result);
    }

    @Test
    void testCreateIrsaSecretName() {
        String result = podCreator.createIrsaSecretName(
                new IsolatedDockerAgentRequest(null, resultKey, requestUuid, 0, "bk", 0, true));
        assertEquals(EXPECTED_IRSA_NAME, result);
    }

    @Test
    void testCreateIamRequestName() {
        String result = podCreator.createIamRequestName(
                new IsolatedDockerAgentRequest(null, resultKey, requestUuid, 0, "bk", 0, true));
        assertEquals(EXPECTED_IAM_REQUEST_NAME, result);
    }

    @Test
    void testCreatePodNameExactly51Chars() {
        String result = podCreator.createPodName(new IsolatedDockerAgentRequest(
                null, "shar-servicedeskembeddablesservicedeskemb-bd-45555", requestUuid, 0, "bk", 0, true));
        assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9", result);
    }

    @Test
    void testCreatePodNameExactly52Chars() {
        String result = podCreator.createPodName(new IsolatedDockerAgentRequest(
                null, "shar-servicedeskembeddablesservicedeskemb-bdx-45555", requestUuid, 0, "bk", 0, true));
        assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9", result);
    }

    @Test
    void testRole() throws IOException {
        mockGlobalConfiguration();
        mockDarkFeatureManager(Optional.of(false));

        Configuration config = ConfigurationBuilder.create(IMAGE_NAME)
                .withAwsRole("arn:aws:iam::123456789012:role/testrole")
                .withImageSize(Configuration.ContainerSize.REGULAR)
                .withExtraContainer("extra-container", "testExtraContainer", Configuration.ExtraContainerSize.REGULAR)
                .build();

        IsolatedDockerAgentRequest request =
                new IsolatedDockerAgentRequest(config, resultKey, requestUuid, 0, "bk", 0, true);

        Map<String, Object> podRequest = podCreator.create(request);
        assertRootKeys(podRequest);
        assertMetadata((Map<String, Object>) podRequest.get("metadata"), true);
        Map<String, Object> spec = (Map<String, Object>) podRequest.get("spec");
        assertSpec(spec);

        List<Map<String, Object>> containers = (List<Map<String, Object>>) spec.get("containers");

        // Test env variables
        for (Map<String, Object> container : containers) {
            assertIamContainerEnvVariables(container);
        }

        for (Map<String, Object> container : containers) {
            assertIamContainerVolumeMounts(container);
        }

        // Test Volumes
        Map<String, Object> iamTokenVolume = getVolume(spec, "aws-iam-token");

        String irsaVolume = yaml.dump(iamTokenVolume);
        String expectedVolume = FileUtils.readFileToString(
                new File(this.getClass().getResource("/irsaVolume.yaml").getFile()), "UTF-8");

        assertEquals(expectedVolume, irsaVolume);
    }

    @Test
    void testNoRole() {
        mockGlobalConfiguration();
        mockDarkFeatureManager(Optional.of(false));

        Configuration config = ConfigurationBuilder.create(IMAGE_NAME)
                .withImageSize(Configuration.ContainerSize.REGULAR)
                .build();

        IsolatedDockerAgentRequest request =
                new IsolatedDockerAgentRequest(config, resultKey, requestUuid, 0, "bk", 0, true, "someRandomToken");

        Map<String, Object> podRequest = podCreator.create(request);
        assertRootKeys(podRequest);
        assertMetadata((Map<String, Object>) podRequest.get("metadata"), false);
        Map<String, Object> spec = (Map<String, Object>) podRequest.get("spec");
        assertSpec(spec);
        Map<String, Object> mainContainer = getMainContainer(spec);

        // Test env variables
        Map<String, String> envVariables = getEnvVariablesFromContainer(mainContainer);
        assertFalse(envVariables.containsKey("AWS_ROLE_ARN"));
        assertFalse(envVariables.containsKey("AWS_WEB_IDENTITY_TOKEN_FILE"));
        assertEquals("someRandomToken", envVariables.get("SECURITY_TOKEN"));

        // Test volume mounts
        Map<String, Object> iamTokenVolumeMount = getVolumeMount(mainContainer, "aws-iam-token");
        assertNull(iamTokenVolumeMount);

        // Test Volumes
        Map<String, Object> iamTokenVolume = getVolume(spec, "aws-iam-token");
        assertNull(iamTokenVolume);
    }

    @Test
    void testEphemeralWhenDarkFeatureTrue() {
        testEphemeral(Optional.of(true), "true");
    }

    @Test
    void testEphemeralWhenDarkFeatureFalse() {
        testEphemeral(Optional.of(false), "false");
    }

    @Test
    void testEphemeralWhenDarkFeatureUndefined() {
        testEphemeral(Optional.empty(), "false");
    }

    @Test
    void testAgentHeartbeatTimeLong() {
        testHeartbeat(60);
    }

    @Test
    void testAgentHeartbeatTimeShort() {
        testHeartbeat(10);
    }

    @Test
    void testIAMRequest() throws IOException {
        Configuration config = ConfigurationBuilder.create(IMAGE_NAME)
                .withAwsRole("arn:aws:iam::123456789012:role/testrole")
                .withImageSize(Configuration.ContainerSize.REGULAR)
                .build();

        IsolatedDockerAgentRequest request =
                new IsolatedDockerAgentRequest(config, "TEST-PLAN-JOB-1", requestUuid, 0, "bk", 0, true);
        Map<String, Object> iamRequest = podCreator.createIamRequest(request, "test-bamboo/TEST-PLAN/abc123");

        String expectedIamRequest = FileUtils.readFileToString(
                new File(this.getClass().getResource("/iamRequest.yaml").getFile()), "UTF-8");

        assertEquals(expectedIamRequest, yaml.dump(iamRequest));
    }

    private void testEphemeral(Optional<Boolean> darkFeatureResult, String expected) {
        mockGlobalConfiguration();
        mockDarkFeatureManager(darkFeatureResult);

        Configuration config = ConfigurationBuilder.create(IMAGE_NAME)
                .withImageSize(Configuration.ContainerSize.REGULAR)
                .build();

        IsolatedDockerAgentRequest request =
                new IsolatedDockerAgentRequest(config, resultKey, requestUuid, 0, "bk", 0, true, "someRandomToken");

        Map<String, Object> podRequest = podCreator.create(request);
        assertRootKeys(podRequest);
        assertMetadata((Map<String, Object>) podRequest.get("metadata"), false);
        Map<String, Object> spec = (Map<String, Object>) podRequest.get("spec");
        assertSpec(spec);
        assertEphemeral(expected, spec);
    }

    private void testHeartbeat(Integer agentHeartbeatTime) {
        mockGlobalConfiguration(agentHeartbeatTime);
        mockDarkFeatureManager(Optional.of(true));
        Configuration config = ConfigurationBuilder.create(IMAGE_NAME)
                .withImageSize(Configuration.ContainerSize.REGULAR)
                .build();

        IsolatedDockerAgentRequest request =
                new IsolatedDockerAgentRequest(config, resultKey, requestUuid, 0, "bk", 0, true, "someRandomToken");

        Map<String, Object> podRequest = podCreator.create(request);
        assertRootKeys(podRequest);
        assertMetadata((Map<String, Object>) podRequest.get("metadata"), false);
        Map<String, Object> spec = (Map<String, Object>) podRequest.get("spec");
        assertSpec(spec);
        assertAgentHeartbeatTime(Integer.toString(agentHeartbeatTime), spec);
    }

    private void mockGlobalConfiguration(Integer agentHeartbeatTime) {
        when(globalConfiguration.getBambooBaseUrl()).thenReturn(BAMBOO_BASE_URL);
        when(globalConfiguration.getBambooBaseUrlAskKubeLabel()).thenReturn(BAMBOO_BASE_URL);
        when(globalConfiguration.getSizeDescriptor()).thenReturn(new DefaultContainerSizeDescriptor());
        when(globalConfiguration.getCurrentSidekick()).thenReturn(SIDEKICK_IMAGE);
        when(globalConfiguration.getAgentHeartbeatTime()).thenReturn(agentHeartbeatTime);
    }

    private void mockGlobalConfiguration() {
        mockGlobalConfiguration(60);
    }

    private void mockDarkFeatureManager(Optional<Boolean> ephemeral) {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(ephemeral);
    }

    private void assertAllKeys(String[] expectedKeys, Map<String, Object> map) {
        for (String expectedKey : expectedKeys) {
            assertTrue(map.containsKey(expectedKey), expectedKey);
        }
    }

    private void assertRootKeys(Map<String, Object> podRequest) {
        assertAllKeys(new String[] {"apiVersion", "kind", "metadata", "spec"}, podRequest);
        assertEquals("v1", podRequest.get("apiVersion"));
        assertEquals("Pod", podRequest.get("kind"));
    }

    private void assertMetadata(Map<String, Object> metadata, boolean iamRole) {
        assertAllKeys(new String[] {"name", "labels", "annotations"}, metadata);
        assertEquals(EXPECTED_POD_NAME, metadata.get("name"));
        assertLabels((Map<String, Object>) metadata.get("labels"));
        assertAnnotations((Map<String, Object>) metadata.get("annotations"), iamRole);
    }

    private void assertLabels(Map<String, Object> labels) {
        assertAllKeys(
                new String[] {
                    PodCreator.LABEL_PBC_MARKER,
                    PodCreator.ANN_RESULTID,
                    PodCreator.ANN_UUID,
                    PodCreator.LABEL_BAMBOO_SERVER
                },
                labels);
        assertEquals("true", labels.get(PodCreator.LABEL_PBC_MARKER));
        assertEquals(EXPECTED_ANNOTATION_RESULTID, labels.get(PodCreator.ANN_RESULTID));
        assertEquals(requestUuid.toString(), labels.get(PodCreator.ANN_UUID));
        assertEquals(BAMBOO_BASE_URL, labels.get(PodCreator.LABEL_BAMBOO_SERVER));
    }

    private void assertAnnotations(Map<String, Object> annotations, boolean iamRole) {
        assertAllKeys(
                new String[] {
                    PodCreator.ANN_UUID, PodCreator.ANN_RESULTID, PodCreator.ANN_RETRYCOUNT,
                },
                annotations);
        assertEquals(requestUuid.toString(), annotations.get(PodCreator.ANN_UUID));
        assertEquals(resultKey, annotations.get(PodCreator.ANN_RESULTID));
        assertEquals("0", annotations.get(PodCreator.ANN_RETRYCOUNT));
        if (iamRole) {
            assertTrue(annotations.containsKey(PodCreator.ANN_IAM_REQUEST_NAME));
            assertEquals(EXPECTED_IAM_REQUEST_NAME, annotations.get(PodCreator.ANN_IAM_REQUEST_NAME));
        } else {
            assertFalse(annotations.containsKey(PodCreator.ANN_IAM_REQUEST_NAME));
        }
    }

    private void assertSpec(Map<String, Object> spec) {
        assertAllKeys(
                new String[] {"restartPolicy", "hostname", "volumes", "containers", "initContainers", "hostAliases"},
                spec);
        assertEquals("Never", spec.get("restartPolicy"));
        assertEquals("shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp", spec.get("hostname"));
        Map<String, Object> mainContainer = getMainContainer(spec);
        assertAllKeys(new String[] {"image"}, mainContainer);
        assertEquals(IMAGE_NAME, mainContainer.get("image"));
    }

    private void assertIamContainerEnvVariables(Map<String, Object> container) {
        Map<String, String> envVariables = getEnvVariablesFromContainer(container);

        assertTrue(envVariables.containsKey("AWS_ROLE_ARN"));
        assertTrue(envVariables.containsKey("AWS_WEB_IDENTITY_TOKEN_FILE"));

        assertEquals("arn:aws:iam::123456789012:role/testrole", envVariables.get("AWS_ROLE_ARN"));
        assertEquals(
                "/var/run/secrets/eks.amazonaws.com/serviceaccount/token",
                envVariables.get("AWS_WEB_IDENTITY_TOKEN_FILE"));
    }

    private void assertIamContainerVolumeMounts(Map<String, Object> container) {
        Map<String, Object> iamTokenVolumeMount = getVolumeMount(container, "aws-iam-token");

        assertEquals("aws-iam-token", iamTokenVolumeMount.get("name"));
        assertEquals("/var/run/secrets/eks.amazonaws.com/serviceaccount/", iamTokenVolumeMount.get("mountPath"));
        assertEquals(true, iamTokenVolumeMount.get("readOnly"));
    }

    private void assertEphemeral(String expectedEphemeral, Map<String, Object> spec) {
        Map<String, Object> mainContainer = getMainContainer(spec);

        // Test env variables
        Map<String, String> envVariables = getEnvVariablesFromContainer(mainContainer);
        assertTrue(envVariables.containsKey("EPHEMERAL"));
        assertEquals(expectedEphemeral, envVariables.get("EPHEMERAL"));
    }

    private void assertAgentHeartbeatTime(String expectedHeartbeatTime, Map<String, Object> spec) {
        Map<String, Object> mainContainer = getMainContainer(spec);

        Map<String, String> envVariables = getEnvVariablesFromContainer(mainContainer);
        assertTrue(envVariables.containsKey("HEARTBEAT"));
        assertEquals(expectedHeartbeatTime, envVariables.get("HEARTBEAT"));
    }

    private Map<String, Object> getMainContainer(Map<String, Object> spec) {
        List<Map<String, Object>> containers = (List<Map<String, Object>>) spec.get("containers");
        assertNotEquals(0, containers.size());
        return containers.get(containers.size() - 1);
    }

    private Map<String, String> getEnvVariablesFromContainer(Map<String, Object> container) {
        List<Map<String, String>> env = (List<Map<String, String>>) container.get("env");
        return env.stream().collect(Collectors.toMap(v -> v.get("name"), v -> v.get("value")));
    }

    private Map<String, Object> getVolumeMount(Map<String, Object> container, String volumeName) {
        List<Map<String, Object>> volumeMounts = (List<Map<String, Object>>) container.get("volumeMounts");
        List<Map<String, Object>> foundVolumeMounts = volumeMounts.stream()
                .filter(vm -> vm.get("name").equals(volumeName))
                .collect(Collectors.toList());
        return foundVolumeMounts.isEmpty() ? null : foundVolumeMounts.get(0);
    }

    private Map<String, Object> getVolume(Map<String, Object> spec, String volumeName) {
        List<Map<String, Object>> volumes = (List<Map<String, Object>>) spec.get("volumes");
        List<Map<String, Object>> foundVolumes =
                volumes.stream().filter(v -> v.get("name").equals(volumeName)).collect(Collectors.toList());
        return foundVolumes.isEmpty() ? null : foundVolumes.get(0);
    }
}
