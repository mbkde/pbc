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

import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.DefaultBandanaManager;
import com.atlassian.bandana.impl.MemoryBandanaPersister;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 * @author mkleint
 */
@RunWith(MockitoJUnitRunner.class)
public class GlobalConfigurationPersistenceTest {
     
    
    @Test
    public void testVersion1() {
        DefaultBandanaManager dbm = new DefaultBandanaManager(new MemoryBandanaPersister());
        AdministrationConfigurationAccessor administrationAccessor = mock(AdministrationConfigurationAccessor.class);
        GlobalConfiguration gc = new GlobalConfiguration(dbm, administrationAccessor);
        
        String persistedValue = "{'image'='aaa'}";
        Configuration conf = gc.load(persistedValue);
        assertNotNull(conf);
        assertEquals("aaa", conf.getDockerImage());
        assertEquals(Configuration.ContainerSize.REGULAR, conf.getSize());
        assertEquals(Collections.emptyList(), conf.getExtraContainers());
    }
    
    @Test
    public void testVersion2() {
        DefaultBandanaManager dbm = new DefaultBandanaManager(new MemoryBandanaPersister());
        AdministrationConfigurationAccessor administrationAccessor = mock(AdministrationConfigurationAccessor.class);
        GlobalConfiguration  gc = new GlobalConfiguration(dbm, administrationAccessor);
        
        String persistedValue = "{'image'='aaa','size'='SMALL'}";
        Configuration conf = gc.load(persistedValue);
        assertNotNull(conf);
        assertEquals("aaa", conf.getDockerImage());
        assertEquals(Configuration.ContainerSize.SMALL, conf.getSize());
        assertEquals(Collections.emptyList(), conf.getExtraContainers());
    }
    
    @Test
    public void testVersion3() {
        DefaultBandanaManager dbm = new DefaultBandanaManager(new MemoryBandanaPersister());
        AdministrationConfigurationAccessor administrationAccessor = mock(AdministrationConfigurationAccessor.class);
        GlobalConfiguration gc = new GlobalConfiguration(dbm, administrationAccessor);
        
        String persistedValue = "{'image'='aaa','size'='SMALL','extraContainers':[{'name':'bbb','image':'bbb-image','size':'SMALL'}]}";
        Configuration conf = gc.load(persistedValue);
        assertNotNull(conf);
        assertEquals("aaa", conf.getDockerImage());
        assertEquals(Configuration.ContainerSize.SMALL, conf.getSize());
        assertEquals(1, conf.getExtraContainers().size());
        Configuration.ExtraContainer extra = conf.getExtraContainers().get(0);
        assertEquals("bbb", extra.getName());
        assertEquals("bbb-image", extra.getImage());
        assertEquals(Configuration.ExtraContainerSize.SMALL, extra.getExtraSize());
    }
}
