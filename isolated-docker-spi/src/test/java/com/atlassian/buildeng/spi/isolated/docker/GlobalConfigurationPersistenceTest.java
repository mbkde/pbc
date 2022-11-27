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

package com.atlassian.buildeng.spi.isolated.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import org.junit.jupiter.api.Test;

public class GlobalConfigurationPersistenceTest {


    @Test
    public void testVersion1() {
        String persistedValue = "{'image'='aaa'}";
        Configuration conf = ConfigurationPersistence.toConfiguration(persistedValue);
        assertNotNull(conf);
        assertEquals("aaa", conf.getDockerImage());
        assertEquals(Configuration.ContainerSize.REGULAR, conf.getSize());
        assertEquals(Collections.emptyList(), conf.getExtraContainers());
    }

    @Test
    public void testVersion2() {
        String persistedValue = "{'image'='aaa','size'='SMALL'}";
        Configuration conf = ConfigurationPersistence.toConfiguration(persistedValue);
        assertNotNull(conf);
        assertEquals("aaa", conf.getDockerImage());
        assertEquals(Configuration.ContainerSize.SMALL, conf.getSize());
        assertEquals(Collections.emptyList(), conf.getExtraContainers());
    }

    @Test
    public void testVersion3() {
        String persistedValue =
                "{'image'='aaa','size'='SMALL','extraContainers':[{'name':'bbb','image':'bbb-image','size':'SMALL'}]}";
        Configuration conf = ConfigurationPersistence.toConfiguration(persistedValue);
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
