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
package com.atlassian.buildeng.ecs;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.rest.Config;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.google.common.base.Objects;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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
    public void getTaskDefinitionNameWithInvalidCharacters() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(conf.getInstanceName()).thenReturn("Bad! -name1");
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        assertEquals("Bad-name1" + Constants.TASK_DEFINITION_SUFFIX, configuration.getTaskDefinitionName());
    }

    @Test
    public void getTaskDefinitionNameTooLong() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        char[] chars = new char[300];
        Arrays.fill(chars, 'c');
        String name = new String(chars);
        when(conf.getInstanceName()).thenReturn(name);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        assertEquals(name.substring(0, 255 - Constants.TASK_DEFINITION_SUFFIX.length()) + Constants.TASK_DEFINITION_SUFFIX, configuration.getTaskDefinitionName());
    }

    @Test
    public void getTaskDefinitionNameNoChanges() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        String name = "Good-name2";
        when(conf.getInstanceName()).thenReturn(name);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        assertEquals(name + Constants.TASK_DEFINITION_SUFFIX, configuration.getTaskDefinitionName());
    }

    @Test
    public void setSidekickHappyPath() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        Config config = new Config("x", "x", "newSidekick");
        configuration.setConfig(config);
        verify(bandanaManager, times(1)).setValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_SIDEKICK_KEY), eq("newSidekick"));
    }
    
    @Test
    public void setSidekickAudited() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(bandanaManager.getValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_CLUSTER_KEY))).thenReturn("cluster1");
        when(bandanaManager.getValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_SIDEKICK_KEY))).thenReturn("sidekick1");
        Config config = new Config("cluster1", "asg1", "newsidekick");
        configuration.setConfig(config);
        verify(auditLogService, times(1)).log(matches("sidekick1", "newsidekick"));
        verify(auditLogService, never()).log(matches("asg1", "asg1"));
        verify(auditLogService, never()).log(matches("cluster1", "cluster1"));
    }

    @Test
    public void setClusterAsgAudited() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(bandanaManager.getValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_CLUSTER_KEY))).thenReturn("cluster1");
        when(bandanaManager.getValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_ASG_KEY))).thenReturn("asg1");
        when(bandanaManager.getValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_SIDEKICK_KEY))).thenReturn("sidekick1");
        Config config = new Config("newcluster", "newasg", "sidekick1");
        configuration.setConfig(config);
        verify(auditLogService, times(1)).log(matches("cluster1", "newcluster"));
        verify(auditLogService, times(1)).log(matches("asg1", "newasg"));
        verify(auditLogService, never()).log(matches("sidekick1", "sidekick1"));
    }

    AuditLogEntry matches(final String oldValue, final String newValue) {
        return Matchers.argThat(item -> {
            System.out.println("m:" + item.getClass());
            AuditLogEntry m =  item;
            return Objects.equal(oldValue, m.getOldValue()) && Objects.equal(newValue, m.getNewValue());
        });
    }
}
