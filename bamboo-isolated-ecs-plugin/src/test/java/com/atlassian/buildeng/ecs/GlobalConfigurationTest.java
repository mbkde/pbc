/*
 * Copyright 2016 Atlassian.
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
package com.atlassian.buildeng.ecs;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.rest.Config;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 * @author mkleint
 */
@RunWith(MockitoJUnitRunner.class)
public class GlobalConfigurationTest {
    
    @Mock
    private BandanaManager bandanaManager;
    
    @Mock
    private AdministrationConfigurationAccessor administrationAccessor;
    
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private BambooAuthenticationContext authenticationContext;
    
    @InjectMocks
    GlobalConfiguration configuration;
    
    public GlobalConfigurationTest() {
    }
    
    Configuration of(String name) {
        return ConfigurationBuilder.create(name).build();
    }

    @Test
    public void setSidekickHappyPath() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        Config config = new Config("x", "x", "newSidekick");
        configuration.setConfig(config);
        verify(bandanaManager, times(1)).setValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_SIDEKICK_KEY), eq("newSidekick"));
    }
    
}
