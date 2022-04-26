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

package com.atlassian.buildeng.ecs.scheduling;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import static com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations.sanitizeImageName;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;


/**
 *
 * @author mkleint
 */
@ExtendWith(MockitoExtension.class)
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
        when(ecsConfiguration.getSizeDescriptor()).thenReturn(new DefaultContainerSizeDescriptor());
        when(backend.getAllECSTaskRegistrations()).thenReturn(task);
        when(backend.getAllRegistrations()).thenReturn(dock);
        BambooServerEnvironment env = mock(BambooServerEnvironment.class);

        when(regs.ecsClient.registerTaskDefinition(any())).thenReturn(new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        Configuration c = ConfigurationBuilder.create("aaa")
                .withImageSize(Configuration.ContainerSize.SMALL)
                .withExtraContainer("extra", "extra", Configuration.ExtraContainerSize.SMALL)
                .build();
        try {
            int val = regs.registerDockerImage(c, env);
            Assertions.assertEquals(4, val);
            Assertions.assertEquals(4, regs.findTaskRegistrationVersion(c, env));
        } catch (ECSException ex) {
            Assertions.fail(ex.getMessage());
        }
        
        //next time round we should not add anything.
        try {
            int val = regs.registerDockerImage(c, env);
            Assertions.assertEquals(4, val);
        } catch (ECSException ex) {
            Assertions.fail(ex.getMessage());
        }
    }

    @Test
    public void serializeTest() throws Exception {
        Map<Configuration, Integer> dock = new HashMap<>();
        Map<String, Integer> task = new HashMap<>();
        when(backend.getAllECSTaskRegistrations()).thenReturn(task);
        when(backend.getAllRegistrations()).thenReturn(dock);
        when(ecsConfiguration.getSizeDescriptor()).thenReturn(new DefaultContainerSizeDescriptor());
        
        when(regs.ecsClient.registerTaskDefinition(any())).then(invocation -> new RegisterTaskDefinitionResult().withTaskDefinition(new TaskDefinition().withRevision(4)));
        BambooServerEnvironment env = mock(BambooServerEnvironment.class);

        Configuration c = ConfigurationBuilder.create("image").build();
        Integer number = regs.registerDockerImage(c, env);
        Integer number2 = regs.findTaskRegistrationVersion(c, env);
        Assertions.assertEquals(number, number2);
        Configuration c2 = ConfigurationBuilder.create("image").withImageSize(Configuration.ContainerSize.SMALL).build();

        int notExisting = regs.findTaskRegistrationVersion(c2, env);
        Assertions.assertEquals(-1, notExisting);

    }

    @Test
    public void sanitizeTest() throws Exception {
        Assertions.assertEquals("image:tag", sanitizeImageName("\t\n\f\r image:tag\t\n\f\r "));
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
