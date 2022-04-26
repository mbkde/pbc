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

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@ExtendWith(MockitoExtension.class)
public class PodCreatorTest {

    private GlobalConfiguration globalConfiguration;
    private Yaml yaml;

    public PodCreatorTest() {
    }

    @BeforeEach
    public void setUp() {


        globalConfiguration = mock(GlobalConfiguration.class);
        Mockito.lenient().when(globalConfiguration.getBambooBaseUrl()).thenReturn("http://test-bamboo.com");
        Mockito.lenient().when(globalConfiguration.getSizeDescriptor()).thenReturn(new DefaultContainerSizeDescriptor());
        Mockito.lenient().when(globalConfiguration.getCurrentSidekick()).thenReturn("sidekickImage");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setCanonical(false);
        yaml = new Yaml(options);

    }

    @Test
    public void testCreatePodName() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp-455", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        Assertions.assertEquals("shardspipeline-servicedeskembeddablesservicede-455-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
        
    }

    @Test
    public void testCreateIrsaSecretName() {
        String result = PodCreator.createIrsaSecretName(
            new IsolatedDockerAgentRequest(null,
                "shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp-455",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        Assertions.assertEquals("iamtoken-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
            result);

    }

    @Test
    public void testCreateIamRequestName() {
        String result = PodCreator.createIamRequestName(
            new IsolatedDockerAgentRequest(null,
                "shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp-455",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        Assertions.assertEquals("shardspipeline-servicedeskembeddabl-455-iamrequest-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
            result);

    }
    
    @Test
    public void testCreatePodNameExactly51Chars() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shar-servicedeskembeddablesservicedeskemb-bd-45555", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        Assertions.assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
    }
    
    @Test
    public void testCreatePodNameExactly52Chars() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shar-servicedeskembeddablesservicedeskemb-bdx-45555", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        Assertions.assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
    }

    @Test
    public void testRole() throws IOException {

        Configuration config = ConfigurationBuilder.create("testImage")
            .withAwsRole("arn:aws:iam::123456789012:role/testrole")
            .withImageSize(Configuration.ContainerSize.REGULAR)
            .withExtraContainer("extra-container", "testExtraContainer", Configuration.ExtraContainerSize.REGULAR)
            .build();

        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(config,
            "TEST-PLAN-JOB-1",
            UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
            0, "bk", 0, true);

        Map<String, Object> podRequest = PodCreator.create(request, globalConfiguration);
        Map<String, Object> spec = (Map<String, Object>) podRequest.get("spec");
        Map<String, Object> metadata = (Map<String, Object>) podRequest.get("metadata");
        Map<String, String> annotations = (Map<String, String>) metadata.get("annotations");

        Assertions.assertTrue(annotations.containsKey("pbc.iamRequestName"));
        Assertions.assertEquals("test-plan-job-1-iamrequest-379ad7b0-b4f5-4fae-914b-070e9442c0a9", annotations.get("pbc.iamRequestName"));

        List<Map<String, Object>> containers = (List<Map<String, Object>>) spec.get("containers");

        //Test env variables
        for (Map<String, Object> container : containers) {
            Map<String, String> envVariables = getEnvVariablesFromContainer(container);

            Assertions.assertTrue(envVariables.containsKey("AWS_ROLE_ARN"));
            Assertions.assertTrue(envVariables.containsKey("AWS_WEB_IDENTITY_TOKEN_FILE"));

            Assertions.assertEquals("arn:aws:iam::123456789012:role/testrole", envVariables.get("AWS_ROLE_ARN"));
            Assertions.assertEquals("/var/run/secrets/eks.amazonaws.com/serviceaccount/token", envVariables.get("AWS_WEB_IDENTITY_TOKEN_FILE"));
        }

        for (Map<String, Object> container : containers) {
            //Test Volume mounts
            Map<String, Object> iamTokenVolumeMount = getVolumeMount(container, "aws-iam-token");

            Assertions.assertEquals("aws-iam-token", iamTokenVolumeMount.get("name"));
            Assertions.assertEquals("/var/run/secrets/eks.amazonaws.com/serviceaccount/", iamTokenVolumeMount.get("mountPath"));
            Assertions.assertEquals(true, iamTokenVolumeMount.get("readOnly"));
        }

        //Test Volumes
        Map<String, Object> iamTokenVolume = getVolume(spec, "aws-iam-token");

        String irsaVolume = yaml.dump(iamTokenVolume);
        String expectedVolume = FileUtils.readFileToString(new File(this.getClass().getResource("/irsaVolume.yaml").getFile()), "UTF-8");

        Assertions.assertEquals(expectedVolume, irsaVolume);
    }

    @Test
    public void testNoRole() {
        Configuration config = ConfigurationBuilder.create("testImage")
            .withImageSize(Configuration.ContainerSize.REGULAR)
            .build();

        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(config,
            "TEST-PLAN-JOB-1",
            UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
            0, "bk", 0, true);

        Map<String, Object> podRequest = PodCreator.create(request, globalConfiguration);
        Map<String, Object> spec = (Map<String, Object>) podRequest.get("spec");
        List<Map<String, Object>> containers = (List<Map<String, Object>>) spec.get("containers");
        Map<String, Object> mainContainer = containers.get(0);

        //Test env variables
        Map<String, String> envVariables = getEnvVariablesFromContainer(mainContainer);
        Assertions.assertFalse(envVariables.containsKey("AWS_ROLE_ARN"));
        Assertions.assertFalse(envVariables.containsKey("AWS_WEB_IDENTITY_TOKEN_FILE"));

        //Test volume mounts
        Map<String, Object> iamTokenVolumeMount = getVolumeMount(mainContainer, "aws-iam-token");
        Assertions.assertNull(iamTokenVolumeMount);

        //Test Volumes
        Map<String, Object> iamTokenVolume = getVolume(spec, "aws-iam-token");
        Assertions.assertNull(iamTokenVolume);

    }

    @Test
    public void testIAMRequest() throws IOException {
        Configuration config = ConfigurationBuilder.create("testImage")
            .withAwsRole("arn:aws:iam::123456789012:role/testrole")
            .withImageSize(Configuration.ContainerSize.REGULAR)
            .build();

        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(config,
            "TEST-PLAN-JOB-1",
            UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
            0, "bk", 0, true);

        Map<String, Object> iamRequest = PodCreator.createIamRequest(request, globalConfiguration, "test-bamboo/TEST-PLAN/abc123");

        String expectedIamRequest = FileUtils.readFileToString(new File(this.getClass().getResource("/iamRequest.yaml").getFile()), "UTF-8");

        Assertions.assertEquals(expectedIamRequest, yaml.dump(iamRequest));

    }

    private Map<String, String> getEnvVariablesFromContainer(Map<String, Object> container) {
        List<Map<String, String>> env = (List<Map<String, String>>) container.get("env");
        return env.stream().collect(Collectors.toMap(v -> v.get("name"), v -> v.get("value")));
    }

    private Map<String, Object> getVolumeMount(Map<String, Object> container, String volumeName) {
        List<Map<String, Object>> volumeMounts = (List<Map<String, Object>>) container.get("volumeMounts");
        List<Map<String,Object>> foundVolumeMounts = volumeMounts.stream().filter(vm -> vm.get("name").equals(volumeName)).collect(Collectors.toList());
        return foundVolumeMounts.isEmpty() ? null : foundVolumeMounts.get(0);
    }

    private Map<String, Object> getVolume(Map <String, Object> spec, String volumeName) {
        List<Map<String, Object>> volumes = (List<Map<String, Object>>) spec.get("volumes");
        List<Map<String, Object>> foundVolumes = volumes.stream().filter(v -> v.get("name").equals(volumeName)).collect(Collectors.toList());
        return foundVolumes.isEmpty() ? null : foundVolumes.get(0);
    }
    
}
