/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.handler;

import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.buildeng.isolated.docker.GlobalConfiguration;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.webresource.WebResourceManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DockerHandlerImplTest {
    @Mock
    ModuleDescriptor moduleDescriptor;
    @Mock
    TemplateRenderer templateRenderer;
    @Mock
    EnvironmentCustomConfigService environmentCustomConfigService;
    @Mock
    WebResourceManager webResourceManager;
    @Mock
    EnvironmentRequirementService environmentRequirementService;
    @Mock
    GlobalConfiguration globalConfiguration;
    @Mock
    Validator validator;

    // LinkedHashMap as we want to preserve the ordering
    private final LinkedHashMap<String, String> archList = new LinkedHashMap<String, String>() {{
        put("default", "Default");
        put("amd64", "Intel x86_64");
        put("arm64", "ARMv8 AArch64");
    }};

    @Test
    public void testGetArchitecturesWithNonEmptyList() {
        Configuration c = ConfigurationBuilder.create("image").build();

        DockerHandlerImpl dockerHandler = new DockerHandlerImpl(moduleDescriptor, webResourceManager,
                templateRenderer, environmentCustomConfigService, environmentRequirementService,
                true, c, globalConfiguration, validator);

        when(globalConfiguration.getArchitectureConfig()).thenReturn(archList);

        Collection<Pair<String, String>> returnedArchList = dockerHandler.getArchitectures();

        List<Pair<String, String>> expectedArchList = Arrays.asList(Pair.make("default", "Default"),
                Pair.make("amd64", "Intel x86_64"),
                Pair.make("arm64", "ARMv8 AArch64"));

        assertEquals(expectedArchList, returnedArchList);
    }

    @Test
    public void testGetArchitecturesWithNonEmptyListAndBuildHasInvalidArchitecture() {
        Configuration c = ConfigurationBuilder.create("image").withArchitecture("fakeArch").build();

        DockerHandlerImpl dockerHandler = new DockerHandlerImpl(moduleDescriptor, webResourceManager,
                templateRenderer, environmentCustomConfigService, environmentRequirementService,
                true, c, globalConfiguration, validator);

        when(globalConfiguration.getArchitectureConfig()).thenReturn(archList);

        Collection<Pair<String, String>> returnedArchList = dockerHandler.getArchitectures();

        List<Pair<String, String>> expectedArchList = Arrays.asList(Pair.make("default", "Default"),
                Pair.make("amd64", "Intel x86_64"),
                Pair.make("arm64", "ARMv8 AArch64"),
                Pair.make("fakeArch", "fakeArch <not supported on this server>"));

        assertEquals(expectedArchList, returnedArchList);
    }

    @Test
    public void testGetArchitecturesWithEmptyListAndBuildHasInvalidArchitecture() {
        Configuration c = ConfigurationBuilder.create("image").withArchitecture("fakeArch").build();

        DockerHandlerImpl dockerHandler = new DockerHandlerImpl(moduleDescriptor, webResourceManager,
                templateRenderer, environmentCustomConfigService, environmentRequirementService,
                true, c, globalConfiguration, validator);

        when(globalConfiguration.getArchitectureConfig()).thenReturn(new LinkedHashMap<>());

        Collection<Pair<String, String>> returnedArchList = dockerHandler.getArchitectures();

        List<Pair<String, String>> expectedArchList = Arrays.asList(Pair.make(null, "<select this option to remove any architecture>"),
                Pair.make("fakeArch", "fakeArch <not supported on this server>"));

        assertEquals(expectedArchList, returnedArchList);
    }
}