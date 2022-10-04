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

import static com.atlassian.buildeng.isolated.docker.GlobalConfiguration.BANDANA_VENDOR_CONFIG;
import static com.atlassian.buildeng.isolated.docker.GlobalConfiguration.VENDOR_AWS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GlobalConfigurationTest {
    private AutoCloseable close;

    private BandanaManager bandanaManager = mock(BandanaManager.class);
    private AdministrationConfigurationAccessor admConfAccessor = mock(AdministrationConfigurationAccessor.class);
    private AuditLogService auditLogService = mock(AuditLogService.class);
    private BambooAuthenticationContext authenticationContext = mock(BambooAuthenticationContext.class);

    @Spy
    GlobalConfiguration globalConfiguration =
            new GlobalConfiguration(bandanaManager, auditLogService, admConfAccessor, authenticationContext);

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
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                com.atlassian.buildeng.isolated.docker.GlobalConfiguration.VENDOR_AWS)).thenReturn(null);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                GlobalConfiguration.BANDANA_IAM_REQUEST_TEMPLATE)).thenReturn(null);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                GlobalConfiguration.BANDANA_IAM_SUBJECT_ID_PREFIX)).thenReturn(null);

        globalConfiguration.migrateAwsVendor();

        verify(bandanaManager, times(0)).setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_VENDOR_CONFIG,
                VENDOR_AWS);
    }

    @Test
    public void testMigrationEnablesIfPriorPbcSettings() {
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                com.atlassian.buildeng.isolated.docker.GlobalConfiguration.VENDOR_AWS)).thenReturn(null);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                GlobalConfiguration.BANDANA_IAM_REQUEST_TEMPLATE)).thenReturn("template");
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                GlobalConfiguration.BANDANA_IAM_SUBJECT_ID_PREFIX)).thenReturn(null);

        globalConfiguration.migrateAwsVendor();

        verify(bandanaManager, times(1)).setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_VENDOR_CONFIG,
                VENDOR_AWS);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testContainersMergedByName() {
        assertEquals("httplocalhost6990bamboo", GlobalConfiguration.stripLabelValue("http://localhost:6990/bamboo"));
        assertEquals("httpsstaging-bamboo.internal.atlassian.com",
                GlobalConfiguration.stripLabelValue("https://staging-bamboo.internal.atlassian.com"));
    }


}
