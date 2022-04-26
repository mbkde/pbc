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
package com.atlassian.buildeng.isolated.docker.sox;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
public class DockerSoxServiceTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private BandanaManager bandanaManager;
    @Mock
    private BambooAuthenticationContext authenticationContext;

    @InjectMocks
    private DockerSoxService dockerSoxService;

    public DockerSoxServiceTest() {
    }

    @Test
    public void soxDisabledByDefault() {
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_ENABLED))).thenReturn(null);

        assertTrue(dockerSoxService.checkSoxCompliance(ConfigurationBuilder.create("aaa").build()));
    }

    @Test
    public void soxDisabledViaSettings() {
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_ENABLED))).thenReturn(Boolean.FALSE);

        assertTrue(dockerSoxService.checkSoxCompliance(ConfigurationBuilder.create("aaa").build()));
    }

    @Test
    public void soxEnabledMainPassing() {
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_ENABLED))).thenReturn(Boolean.TRUE);
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_PATTERNS))).thenReturn(new String[] { "^a.*", "^b.*"});

        assertTrue(dockerSoxService.checkSoxCompliance(ConfigurationBuilder.create("aaa").build()));
    }

    @Test
    public void soxEnabledMainFailing() {
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_ENABLED))).thenReturn(Boolean.TRUE);
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_PATTERNS))).thenReturn(new String[] { "^b.*", "^c.*"});
        assertFalse(dockerSoxService.checkSoxCompliance(ConfigurationBuilder.create("aaa").build()));
    }

    @Test
    public void soxEnabledExtraFailing() {
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_ENABLED))).thenReturn(Boolean.TRUE);
        when(bandanaManager.getValue(
                eq(PlanAwareBandanaContext.GLOBAL_CONTEXT),
                eq(DockerSoxService.BANDANA_SOX_PATTERNS))).thenReturn(new String[] { "^b.*", "^c.*"});
        assertFalse(dockerSoxService.checkSoxCompliance(ConfigurationBuilder.create("ba").withExtraContainer("extra", "aaa", Configuration.ExtraContainerSize.SMALL).build()));
    }

}
