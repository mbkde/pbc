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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class PodCreatorTest {

    private GlobalConfiguration globalConfiguration;

    public PodCreatorTest() {
    }

    @Before
    public void setUp() {


        globalConfiguration = mock(GlobalConfiguration.class);
        when(globalConfiguration.getBambooBaseUrl()).thenReturn("http://test-bamboo.com");
        when(globalConfiguration.getSizeDescriptor()).thenReturn(new DefaultContainerSizeDescriptor());
        when(globalConfiguration.getCurrentSidekick()).thenReturn("sidekickImage");

    }

    @Test
    public void testCreatePodName() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp-455", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        assertEquals("shardspipeline-servicedeskembeddablesservicede-455-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
        
    }
    
    @Test
    public void testCreatePodNameExactly51Chars() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shar-servicedeskembeddablesservicedeskemb-bd-45555", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
    }
    
    @Test
    public void testCreatePodNameExactly52Chars() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shar-servicedeskembeddablesservicedeskemb-bdx-45555", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0, true));
        assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
    }

    @Test
    public void testRole() {

        Configuration config = ConfigurationBuilder.create("testImage")
            .withAwsRole("arn:aws:iam::123456789012:role/testrole")
            .withImageSize(Configuration.ContainerSize.REGULAR)
            .build();

        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(config,
            "TEST-PLAN-JOB-1",
            UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
            0, "bk", 0, true);

        Map<String, Object> podRequest = PodCreator.create(request, globalConfiguration,"test-bamboo:TEST-PLAN:abc123");
        Map<String, Object> metadata = (Map<String, Object>) podRequest.get("metadata");
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");

        assertEquals( "arn:aws:iam::123456789012:role/testrole" , annotations.get("iam.amazonaws.com/role"));
        assertEquals("test-bamboo:TEST-PLAN:abc123", annotations.get("iam.amazonaws.com/external-id"));
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

        Map<String, Object> podRequest = PodCreator.create(request, globalConfiguration,"test-bamboo:TEST-PLAN:abc123");
        Map<String, Object> metadata = (Map<String, Object>) podRequest.get("metadata");
        Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");

        assertNull(annotations.get("iam.amazonaws.com/role"));
        assertNull(annotations.get("iam.amazonaws.com/external-id"));

    }
    
}
