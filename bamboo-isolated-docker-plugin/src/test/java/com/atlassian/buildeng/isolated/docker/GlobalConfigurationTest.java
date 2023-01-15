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

package com.atlassian.buildeng.isolated.docker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GlobalConfigurationTest {
    private AutoCloseable close;

    private final BandanaManager bandanaManager = mock(BandanaManager.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final BambooAuthenticationContext authenticationContext = mock(BambooAuthenticationContext.class);

    GlobalConfiguration globalConfiguration =
            spy(new GlobalConfiguration(bandanaManager, auditLogService, authenticationContext));

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
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_ENABLED_PROPERTY))
                .thenReturn(null);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_DEFAULT_IMAGE))
                .thenReturn(null);
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT,
                        GlobalConfiguration.BANDANA_MAX_AGENT_CREATION_PER_MINUTE))
                .thenReturn(null);
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_ARCHITECTURE_CONFIG_RAW))
                .thenReturn(null);

        globalConfiguration.migrateEnabled();
        verify(globalConfiguration, times(0)).setEnabledProperty(true);
    }

    @Test
    public void testMigrationEnablesIfPriorPbcSettings() {
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_ENABLED_PROPERTY))
                .thenReturn(null);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_DEFAULT_IMAGE))
                .thenReturn("image");
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT,
                        GlobalConfiguration.BANDANA_MAX_AGENT_CREATION_PER_MINUTE))
                .thenReturn(null);
        when(bandanaManager.getValue(
                        PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_ARCHITECTURE_CONFIG_RAW))
                .thenReturn(null);

        globalConfiguration.migrateEnabled();
        verify(globalConfiguration, times(1)).setEnabledProperty(true);
    }
}
