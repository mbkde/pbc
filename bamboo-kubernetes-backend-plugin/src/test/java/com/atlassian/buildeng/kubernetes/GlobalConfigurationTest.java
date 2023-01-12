/*
 * Copyright 2017 Atlassian Pty Ltd.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.kubernetes.rest.Config;
import java.io.IOException;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GlobalConfigurationTest {
    private AutoCloseable close;

    private final BandanaManager bandanaManager = mock(BandanaManager.class);
    private final AdministrationConfigurationAccessor admConfAccessor = mock(AdministrationConfigurationAccessor.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final BambooAuthenticationContext authenticationContext = mock(BambooAuthenticationContext.class);

    GlobalConfiguration globalConfiguration =
            spy(new GlobalConfiguration(bandanaManager, auditLogService, admConfAccessor, authenticationContext));

    @BeforeEach
    public void setup() {
        close = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        close.close();
    }

    @Test
    public void testMigrationDoesntEnableIfNoPriorPbcSettings() {
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT,
                        com.atlassian.buildeng.isolated.docker.GlobalConfiguration.VENDOR_AWS))
                .thenReturn(null);
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_IAM_REQUEST_TEMPLATE))
                .thenReturn(null);
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_IAM_SUBJECT_ID_PREFIX))
                .thenReturn(null);

        try (MockedStatic<com.atlassian.buildeng.isolated.docker.GlobalConfiguration> globalConfigurationMock =
                Mockito.mockStatic(com.atlassian.buildeng.isolated.docker.GlobalConfiguration.class)) {
            globalConfiguration.migrateAwsVendor();
            globalConfigurationMock.verify(
                    () -> com.atlassian.buildeng.isolated.docker.GlobalConfiguration.setVendorWithBandana(any(), any()),
                    times(0));
        }
    }

    @Test
    public void testMigrationEnablesIfPriorPbcSettings() {
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT,
                        com.atlassian.buildeng.isolated.docker.GlobalConfiguration.VENDOR_AWS))
                .thenReturn(null);
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_IAM_REQUEST_TEMPLATE))
                .thenReturn("template");
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_IAM_SUBJECT_ID_PREFIX))
                .thenReturn(null);

        try (MockedStatic<com.atlassian.buildeng.isolated.docker.GlobalConfiguration> globalConfigurationMock =
                Mockito.mockStatic(com.atlassian.buildeng.isolated.docker.GlobalConfiguration.class)) {
            globalConfiguration.migrateAwsVendor();
            globalConfigurationMock.verify(
                    () -> com.atlassian.buildeng.isolated.docker.GlobalConfiguration.setVendorWithBandana(any(), any()),
                    times(1));
        }
    }

    private Config mockConfigDefaults() {
        Config config = mock(Config.class);
        when(config.getSidekickImage()).thenReturn("sidekick-image");
        when(config.getCurrentContext()).thenReturn("");
        when(config.getPodTemplate()).thenReturn("pod-template");
        when(config.getArchitecturePodConfig()).thenReturn("");
        when(config.getIamRequestTemplate()).thenReturn("");
        when(config.getIamSubjectIdPrefix()).thenReturn("");
        when(config.getPodLogsUrl()).thenReturn("");
        when(config.getContainerSizes()).thenReturn("{'main':[],'extra':[]}");
        when(config.isUseClusterRegistry()).thenReturn(false);
        when(config.getClusterRegistryAvailableSelector()).thenReturn("");
        when(config.getClusterRegistryPrimarySelector()).thenReturn("");
        when(config.getArtifactoryCacheAllowList()).thenReturn("");
        return config;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testYamlThrowsExceptionIfInvalid() throws IOException {
        Config config = mockConfigDefaults();
        when(config.getArtifactoryCachePodSpec()).thenReturn("not a valid yaml string");

        globalConfiguration.persist(config);
    }

    @Test
    public void testYamlSetCorrectlyWhenValid() throws IOException {
        Config config = mockConfigDefaults();
        when(config.getArtifactoryCachePodSpec()).thenReturn("heading:\n  - item");

        globalConfiguration.persist(config);
        verify(bandanaManager).setValue(any(), eq(GlobalConfiguration.BANDANA_ARTIFACTORY_CACHE_PODSPEC), any());
    }

    @Test
    public void testContainersMergedByName() {
        assertEquals("httplocalhost6990bamboo", GlobalConfiguration.stripLabelValue("http://localhost:6990/bamboo"));
        assertEquals(
                "httpsstaging-bamboo.internal.atlassian.com",
                GlobalConfiguration.stripLabelValue("https://staging-bamboo.internal.atlassian.com"));
    }
}
