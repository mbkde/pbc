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

package com.atlassian.buildeng.spi.isolated.docker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConfigurationTest {

    @Test
    public void testDefaultRegistry() {
        String dockerHubImage = "docker:17.07.0-ce-dind";
        assertEquals(dockerHubImage, Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap("")));
    }

    @Test
    public void testDefaultRegistry2() {
        String dockerHubImage = "selenium:latest";
        String registryString = "docker.atl-paas.net,bar-foo.com,docker.atlassian.io,foo-bar.com";

        assertEquals(dockerHubImage, Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap(registryString)));
    }

    @Test
    public void testOverrideRegistry() {
        String dockerHubImage = "docker.atl-paas.net/buildeng/agent-baseagent";
        String registryString = "docker.atl-paas.net,bar-foo.com,docker.atlassian.io,foo-bar.com";

        assertEquals("bar-foo.com/buildeng/agent-baseagent", Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap(registryString)));
    }
    
    @Test
    public void testOverrideRegistry2() {
        String dockerHubImage = "docker.atlassian.io/docker:latest";
        String registryString = "docker.atlassian.io,foo-bar.com";

        assertEquals("foo-bar.com/docker:latest", Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap(registryString)));
    }

    @Test
    public void testOverrideRegistry3() {
        String dockerHubImage = "docker.atlassian.io/buildeng/docker:latest";
        String registryString = "docker.atl-paas.net,foo-bar.com";

        assertEquals(dockerHubImage, Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap(registryString)));
    }

    @Test
    public void testOverrideRegistryMalformedMapping() {
        String dockerHubImage = "docker.atlassian.io/buildeng/docker:latest";
        String registryString = "docker.atlassian.io,foo-bar.com,foo-foo";

        assertEquals(dockerHubImage, Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap(registryString)));
    }

    @Test
    public void testOverrideRegistryWithPort() {
        String dockerHubImage = "docker.atlassian.io:90/buildeng/docker:latest";
        String registryString = "docker.atl-paas.net,bar-foo.com,docker.atlassian.io,foo-bar.com";

        assertEquals(dockerHubImage, Configuration.overrideRegistry(dockerHubImage,
                Configuration.registryOverrideStringToMap(registryString)));
    }
}
