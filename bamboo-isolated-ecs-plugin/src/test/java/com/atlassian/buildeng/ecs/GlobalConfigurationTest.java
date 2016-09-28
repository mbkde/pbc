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

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.rest.Config;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import static org.mockito.Matchers.anyObject;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
    
    @InjectMocks
    GlobalConfigurationSubclass configuration;
    
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
    
    @Test
    public void registerTaskDefinition() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        Map<String, Integer> dock = new HashMap<>();
        Map<String, Integer> task = new HashMap<>();
        
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_DOCKER_MAPPING_KEY))
                .thenReturn(dock);        
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, GlobalConfiguration.BANDANA_ECS_TASK_MAPPING_KEY))
                .thenReturn(task);        
        when(configuration.ecsClient.registerTaskDefinition(anyObject())).thenReturn(new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        Mockito.doAnswer((Answer<Object>) (InvocationOnMock invocation) -> {
            Map dock2 = invocation.getArgumentAt(2, Map.class);
            dock.clear();
            dock.putAll(dock2);
            return null;
        }).when(bandanaManager).setValue(eq(PlanAwareBandanaContext.GLOBAL_CONTEXT), eq(GlobalConfiguration.BANDANA_DOCKER_MAPPING_KEY), anyObject());
        Configuration c = ConfigurationBuilder.create("aaa")
                .withImageSize(Configuration.ContainerSize.SMALL)
                .withExtraContainer("extra", "extra", Configuration.ExtraContainerSize.SMALL)
                .build();
        try {
            int val = configuration.registerDockerImage(c);
            Assert.assertEquals(4, val);
            Assert.assertEquals(4, configuration.findTaskRegistrationVersion(c));
        } catch (ImageAlreadyRegisteredException | ECSException ex) {
            Assert.fail(ex.getMessage());
        }
        
        //next time round we should not add anything.
        try {
            int val = configuration.registerDockerImage(c);
            Assert.fail("Cannot add the same config twice");
        } catch (ECSException ex) {
            Assert.fail(ex.getMessage());
        } catch (ImageAlreadyRegisteredException ex) {
            //correct path
        }
    }
    
    public static class GlobalConfigurationSubclass extends GlobalConfiguration {
        AmazonECS ecsClient = mock(AmazonECS.class);
        
        public GlobalConfigurationSubclass(BandanaManager bandanaManager, AdministrationConfigurationAccessor admConfAccessor) {
            super(bandanaManager, admConfAccessor);
        }

        @Override
        protected AmazonECS createClient() {
            return ecsClient;
        }
        
    }
}
