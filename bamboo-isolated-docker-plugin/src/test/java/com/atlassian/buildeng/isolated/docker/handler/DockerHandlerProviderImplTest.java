/*
 * Copyright 2023 Atlassian Pty Ltd.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.GlobalConfiguration;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.sal.api.features.DarkFeatureManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DockerHandlerProviderImplTest {

    DarkFeatureManager darkFeatureManager;
    DockerHandlerProviderImpl dockerHandlerProviderImpl;

    @BeforeEach
    public void setUp() {
        darkFeatureManager = mock(DarkFeatureManager.class);
        dockerHandlerProviderImpl = new DockerHandlerProviderImpl(
                mock(TemplateRenderer.class),
                mock(EnvironmentCustomConfigService.class),
                mock(EnvironmentRequirementService.class),
                mock(WebResourceManager.class),
                mock(GlobalConfiguration.class),
                mock(Validator.class),
                darkFeatureManager);
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        BuildDefinition mockBuildDef = mock(BuildDefinition.class);
        Map<String, String> customConfig = new HashMap<>();
        customConfig.put(DockerHandlerProviderImpl.PBC_KEY, "true");
        when(mockBuildDef.getCustomConfiguration()).thenReturn(customConfig);
        assertTrue(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(mockBuildDef));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionNotPBCAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        BuildDefinition mockBuildDef = mock(BuildDefinition.class);
        Map<String, String> customConfig = new HashMap<>();
        when(mockBuildDef.getCustomConfiguration()).thenReturn(customConfig);
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(mockBuildDef));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionDisabledAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        BuildDefinition mockBuildDef = mock(BuildDefinition.class);
        Map<String, String> customConfig = new HashMap<>();
        customConfig.put(DockerHandlerProviderImpl.PBC_KEY, "false");
        when(mockBuildDef.getCustomConfiguration()).thenReturn(customConfig);
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(mockBuildDef));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionFlagNullAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        BuildDefinition mockBuildDef = mock(BuildDefinition.class);
        Map<String, String> customConfig = new HashMap<>();
        customConfig.put(DockerHandlerProviderImpl.PBC_KEY, null);
        when(mockBuildDef.getCustomConfiguration()).thenReturn(customConfig);
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(mockBuildDef));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionNullAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected((BuildDefinition) null));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionAndDarkFeatureFalse() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(false));
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(mock(BuildDefinition.class)));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithBuildDefinitionAndDarkFeatureUndefined() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.empty());
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(mock(BuildDefinition.class)));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigEmptyAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(new HashMap<>()));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigTrueAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        HashMap<String, String> environmentCustomConfig = new HashMap<>();
        environmentCustomConfig.put(DockerHandlerProviderImpl.PBC_KEY, "true");
        assertTrue(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(environmentCustomConfig));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigFalseAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        HashMap<String, String> environmentCustomConfig = new HashMap<>();
        environmentCustomConfig.put(DockerHandlerProviderImpl.PBC_KEY, "false");
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(environmentCustomConfig));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigValueNullAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        HashMap<String, String> environmentCustomConfig = new HashMap<>();
        environmentCustomConfig.put(DockerHandlerProviderImpl.PBC_KEY, null);
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(environmentCustomConfig));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigNullAndDarkFeatureTrue() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(true));
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected((Map<String, String>) null));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigAndDarkFeatureFalse() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.of(false));
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(new HashMap<>()));
    }

    @Test
    void testIsCustomDedicatedAgentExpectedWithEnvironmentCustomConfigAndDarkFeatureUndefined() {
        when(darkFeatureManager.isEnabledForAllUsers(Constants.PBC_EPHEMERAL_ENABLED))
                .thenReturn(Optional.empty());
        assertFalse(dockerHandlerProviderImpl.isCustomDedicatedAgentExpected(new HashMap<>()));
    }
}
