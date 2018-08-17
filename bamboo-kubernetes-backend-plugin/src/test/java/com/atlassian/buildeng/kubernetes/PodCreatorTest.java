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

import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

public class PodCreatorTest {
    
    public PodCreatorTest() {
    }

    @Test
    public void testCreatePodName() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shardspipeline-servicedeskembeddablesservicedeskembeddables-bdp-455", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0));
        Assert.assertEquals("shardspipeline-servicedeskembeddablesservicede-455-379ad7b0-b4f5-4fae-914b-070e9442c0a9", 
                result);
        
    }
    
    @Test
    public void testCreatePodNameExactly51Chars() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shar-servicedeskembeddablesservicedeskemb-bd-45555", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0));
        Assert.assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
    }
    
    @Test
    public void testCreatePodNameExactly52Chars() {
        String result = PodCreator.createPodName(
                new IsolatedDockerAgentRequest(null, 
                        "shar-servicedeskembeddablesservicedeskemb-bdx-45555", 
                        UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"), 0, "bk", 0));
        Assert.assertEquals("shar-servicedeskembeddablesservicedeskemb-bd-45555-379ad7b0-b4f5-4fae-914b-070e9442c0a9",
                result);
    }
    
}
