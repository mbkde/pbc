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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DockerHostTest {

    public DockerHostTest() {
    }

    @Test
    public void testCompareAgeOnly() {
        DockerHost h1 = new DockerHost(
                1000, 1000, 1000, 1000, "ci1", "i1", "ACTIVE", new Date(new Date().getTime() - 10000), true, "m4.xlarge"
        );
        DockerHost h2 = new DockerHost(
                1000, 1000, 1000, 1000, "ci2", "i2", "ACTIVE", new Date(new Date().getTime() - 5000), true, "m4.xlarge"
        );
        DockerHost h3 = new DockerHost(
                1000, 1000, 1000, 1000, "ci3", "i3", "ACTIVE", new Date(new Date().getTime() - 15000), true, "m4.xlarge"
        );
        DockerHost h4 = new DockerHost(
                1000, 1000, 1000, 1000, "ci4", "i4", "ACTIVE", new Date(new Date().getTime()), true, "m4.xlarge"
        );

        ArrayList<DockerHost> lst = Lists.newArrayList(h1, h2, h3, h4);
        Optional<DockerHost> first = lst.stream().sorted(DockerHost.compareByResourcesAndAge()).findFirst();
        assertEquals(first.get(), h3);
    }

}
