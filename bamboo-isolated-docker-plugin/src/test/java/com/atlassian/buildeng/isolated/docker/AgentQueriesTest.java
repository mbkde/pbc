/*
 * Copyright 2023 Atlassian Pty Ltd.
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
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.buildqueue.RemoteAgentDefinition;
import com.atlassian.bamboo.v2.build.agent.capability.Capability;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilitySet;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentQueriesTest {

    @Test
    void getCapabilityValueFromNull() {
        RemoteAgentDefinition remoteAgentDefinition = mock(RemoteAgentDefinition.class);
        when(remoteAgentDefinition.getCapabilitySet()).thenReturn(null);
        Optional<String> ret = AgentQueries.getCapabilityValue(remoteAgentDefinition, "test");
        Assertions.assertTrue(ret.isEmpty());
    }

    @Test
    void getCapabilityResultFromNoKey() {
        RemoteAgentDefinition remoteAgentDefinition = mock(RemoteAgentDefinition.class);
        CapabilitySet capabilitySet = mock(CapabilitySet.class);
        when(remoteAgentDefinition.getCapabilitySet()).thenReturn(capabilitySet);
        when(capabilitySet.getCapability("test")).thenReturn(null);
        Optional<String> ret = AgentQueries.getCapabilityValue(remoteAgentDefinition, "test");
        Assertions.assertTrue(ret.isEmpty());
    }

    @Test
    void getCapabilityResultGetValueNull() {
        RemoteAgentDefinition remoteAgentDefinition = mock(RemoteAgentDefinition.class);
        CapabilitySet capabilitySet = mock(CapabilitySet.class);
        Capability capability = mock(Capability.class);
        when(remoteAgentDefinition.getCapabilitySet()).thenReturn(capabilitySet);
        when(capabilitySet.getCapability("test")).thenReturn(capability);
        when(capability.getValue()).thenReturn(null);
        Optional<String> ret = AgentQueries.getCapabilityValue(remoteAgentDefinition, "test");
        Assertions.assertTrue(ret.isEmpty());
    }

    @Test
    void getCapabilityResultWhenPresent() {
        RemoteAgentDefinition remoteAgentDefinition = mock(RemoteAgentDefinition.class);
        CapabilitySet capabilitySet = mock(CapabilitySet.class);
        Capability capability = mock(Capability.class);
        when(remoteAgentDefinition.getCapabilitySet()).thenReturn(capabilitySet);
        when(capabilitySet.getCapability("test")).thenReturn(capability);
        when(capability.getValue()).thenReturn("answer");
        Optional<String> ret = AgentQueries.getCapabilityValue(remoteAgentDefinition, "test");
        Assertions.assertTrue(ret.isPresent());
    }
}
