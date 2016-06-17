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
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
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
    
    @InjectMocks
    GlobalConfigurationSubclass configuration;
    
    public GlobalConfigurationTest() {
    }

    @Test
    public void setSidekickHappyPath() {
        Map<Configuration, Integer> map = new HashMap<>();
        map.put(Configuration.of("docker1"), 1);
        map.put(Configuration.of("docker2"), 2);
        map.put(Configuration.of("docker3"), 3);
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY))
                .thenReturn(map);
        when(configuration.ecsClient.registerTaskDefinition(anyObject())).then(invocation -> new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        Collection<Exception> errors = configuration.setSidekick("newSidekick");
        assertTrue(errors.isEmpty());
        assertEquals(3, map.size());
        for (Integer val : map.values()) {
            assertTrue("value greater than 3", val > 3);
        }
    }
    
    @Test 
    public void setSidekickFailedDeregistrations() {
        Map<Configuration, Integer> map = new HashMap<>();
        map.put(Configuration.of("docker1"), 1);
        map.put(Configuration.of("docker2"), 2);
        map.put(Configuration.of("docker3"), 3);
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY))
                .thenReturn(map);
        when(configuration.ecsClient.registerTaskDefinition(anyObject())).then(invocation -> new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        when(configuration.ecsClient.deregisterTaskDefinition(anyObject())).then(invocation -> {
            throw new Exception("Error on deregistering");
        });
        Collection<Exception> errors = configuration.setSidekick("newSidekick");
        assertEquals(3, errors.size());
        assertEquals(3, map.size());
        //all new registration are there..
        for (Integer val : map.values()) {
            assertTrue("value greater than 3", val > 3);
        }
    }
    
   @Test 
    public void setSidekickFailedRegistrations() {
        Map<Configuration, Integer> map = new HashMap<>();
        map.put(Configuration.of("docker1"), 1);
        map.put(Configuration.of("docker2"), 2);
        map.put(Configuration.of("docker3"), 3);
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(administrationAccessor.getAdministrationConfiguration()).thenReturn(conf);
        when(bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY))
                .thenReturn(map);
        when(configuration.ecsClient.registerTaskDefinition(anyObject())).then(invocation -> {
            throw new Exception("Error on registering");
        });
        Collection<Exception> errors = configuration.setSidekick("newSidekick");
        assertEquals(3, errors.size());
        //registrations unsuccessful, removed
        assertEquals(0, map.size());
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
