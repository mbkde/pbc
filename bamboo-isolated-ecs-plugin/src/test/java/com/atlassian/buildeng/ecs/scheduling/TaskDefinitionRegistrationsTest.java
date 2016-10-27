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
package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import static org.mockito.Matchers.anyObject;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.runners.MockitoJUnitRunner;


/**
 *
 * @author mkleint
 */
@RunWith(MockitoJUnitRunner.class)
public class TaskDefinitionRegistrationsTest {
    @Mock
    private TaskDefinitionRegistrations.Backend backend;

    @Mock
    private ECSConfiguration ecsConfiguration;
    
    @InjectMocks
    TaskDefinitionRegistrationsSubclass regs;
    
    public TaskDefinitionRegistrationsTest() {
    }
    
    Configuration of(String name) {
        return ConfigurationBuilder.create(name).build();
    }

    @Test
    public void registerTaskDefinition() {
        Map<Configuration, Integer> dock = new HashMap<>();
        Map<String, Integer> task = new HashMap<>();
        when(backend.getAllECSTaskRegistrations()).thenReturn(task);
        when(backend.getAllRegistrations()).thenReturn(dock);
        BambooServerEnvironment env = mock(BambooServerEnvironment.class);

        when(regs.ecsClient.registerTaskDefinition(anyObject())).thenReturn(new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        Configuration c = ConfigurationBuilder.create("aaa")
                .withImageSize(Configuration.ContainerSize.SMALL)
                .withExtraContainer("extra", "extra", Configuration.ExtraContainerSize.SMALL)
                .build();
        try {
            int val = regs.registerDockerImage(c, env);
            Assert.assertEquals(4, val);
            Assert.assertEquals(4, regs.findTaskRegistrationVersion(c, env));
        } catch (ImageAlreadyRegisteredException | ECSException ex) {
            Assert.fail(ex.getMessage());
        }
        
        //next time round we should not add anything.
        try {
            int val = regs.registerDockerImage(c, env);
            Assert.fail("Cannot add the same config twice");
        } catch (ECSException ex) {
            Assert.fail(ex.getMessage());
        } catch (ImageAlreadyRegisteredException ex) {
            //correct path
        }
    }

    @Test
    public void serializeTest() throws Exception {
        Map<Configuration, Integer> dock = new HashMap<>();
        Map<String, Integer> task = new HashMap<>();
        when(backend.getAllECSTaskRegistrations()).thenReturn(task);
        when(backend.getAllRegistrations()).thenReturn(dock);
        when(regs.ecsClient.registerTaskDefinition(anyObject())).then(invocation -> new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        BambooServerEnvironment env = mock(BambooServerEnvironment.class);

        Configuration c = ConfigurationBuilder.create("image").build();
        Integer number = regs.registerDockerImage(c, env);
        Integer number2 = regs.findTaskRegistrationVersion(c, env);
        assertEquals(number, number2);
        Configuration c2 = ConfigurationBuilder.create("image").withImageSize(Configuration.ContainerSize.SMALL).build();

        int notExisting = regs.findTaskRegistrationVersion(c2, env);
        assertEquals(-1, notExisting);

    }
    
    public static class TaskDefinitionRegistrationsSubclass extends TaskDefinitionRegistrations {
        AmazonECS ecsClient = mock(AmazonECS.class);
        
        public TaskDefinitionRegistrationsSubclass(Backend backend, ECSConfiguration ecsConfiguration) {
            super(backend, ecsConfiguration);
        }

        @Override
        protected AmazonECS createClient() {
            return ecsClient;
        }
        
    }
}
