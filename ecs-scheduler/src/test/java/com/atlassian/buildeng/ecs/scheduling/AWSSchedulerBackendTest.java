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

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration.ExtraContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

public class AWSSchedulerBackendTest {

    @Test
    public void noTouchWithCommand() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:1.12", Configuration.ExtraContainerSize.REGULAR);
        List<String> orig = Arrays.asList("--help");
        t.setCommands(orig);
        assertEquals(orig, AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void noTouchEmpty() {
        ExtraContainer t = new ExtraContainer("aaa", "dockery:1.12-dind", Configuration.ExtraContainerSize.REGULAR);
        List<String> orig = new ArrayList<>();
        t.setCommands(orig);
        assertEquals(orig, AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void matchEmpty() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:1.11.0-dind", Configuration.ExtraContainerSize.REGULAR);
        t.setCommands(new ArrayList<>());
        assertEquals(Arrays.asList("--storage-driver=overlay"), AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void matchWithExistingCommands() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:dind", Configuration.ExtraContainerSize.REGULAR);
        t.setCommands(Arrays.asList("--debug", "--default-gateway-v6"));
        assertEquals(Arrays.asList("--debug", "--default-gateway-v6","--storage-driver=overlay"), AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void matchAndRemoveExistingDriver() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:dind", Configuration.ExtraContainerSize.REGULAR);
        t.setCommands(Arrays.asList("--debug", "-s", "vfs", "--default-gateway-v6"));
        assertEquals(Arrays.asList("--debug", "--default-gateway-v6","--storage-driver=overlay"), AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void matchAndRemoveExistingDriver2() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:dind", Configuration.ExtraContainerSize.REGULAR);
        t.setCommands(Arrays.asList("--debug", "--storage-driver=vfs", "--default-gateway-v6"));
        assertEquals(Arrays.asList("--debug", "--default-gateway-v6","--storage-driver=overlay"), AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void matchAndRemoveExistingDriverOpts() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:dind", Configuration.ExtraContainerSize.REGULAR);
        t.setCommands(Arrays.asList("--debug", "-s", "devicemapper", "--storage-opt","dm.thinpooldev=/dev/mapper/thin-pool", "--default-gateway-v6"));
        assertEquals(Arrays.asList("--debug", "--default-gateway-v6","--storage-driver=overlay"), AWSSchedulerBackend.adjustCommands(t, mockHost(null)));
    }

    @Test
    public void matchEmptyContainerInstanceValue() {
        ExtraContainer t = new ExtraContainer("aaa", "docker:1.11.0-dind", Configuration.ExtraContainerSize.REGULAR);
        t.setCommands(new ArrayList<>());
        assertEquals(Arrays.asList("--storage-driver=overlay2"), AWSSchedulerBackend.adjustCommands(t, mockHost("overlay2")));
    }


    DockerHost mockHost(String value) {
        DockerHost host = Mockito.mock(DockerHost.class);
        when(host.getContainerAttribute(eq(Constants.STORAGE_DRIVER_PROPERTY))).thenReturn(value);
        return host;
    }
}
