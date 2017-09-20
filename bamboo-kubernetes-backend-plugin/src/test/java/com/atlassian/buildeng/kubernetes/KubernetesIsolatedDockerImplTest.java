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

package com.atlassian.buildeng.kubernetes;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class KubernetesIsolatedDockerImplTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testContainersMergedByName() {
        Yaml yaml =  new Yaml(new SafeConstructor());
        String ts = "apiVersion: v1\n"
              + "kind: Pod\n"
              + "metadata:\n"
              + "  name: aws-cli\n"
              + "spec:\n"
                
              + "  containers:\n"
              + "    - name: main\n"
              + "      volumeMounts:\n"
              + "          - name: secrets\n"
              + "            mountPath: /root/.aws\n"
              + "    - name: myContainer2\n"
              + "      image: xueshanf/awscli:latest\n"
              + "  restartPolicy: Never\n"
              + "  volumes:\n"
              + "    - name: secrets\n"
              + "      secret:\n"
              + "        secretName: bitbucket-bamboo\n";
        String os =
                "metadata:\n"
              + "    namespace: buildeng\n"
              + "    annotations:\n"
              + "        iam.amazonaws.com/role: arn:aws:iam::123456678912:role/staging-bamboo\n"
              + "spec:\n"
              + "  containers:\n"
              + "    - name: main\n"
              + "      image: xueshanf/awscli:latest\n"
              + "    - name: myContainer3\n"
              + "      image: xueshanf/awscli:latest\n"
              + "  volumes:\n"
              + "    - name: myvolume\n";

        Map<String, Object> template = (Map<String, Object>) yaml.load(ts);
        Map<String, Object> overrides = (Map<String, Object>) yaml.load(os);
        Map<String, Object> merged = KubernetesIsolatedDockerImpl.mergeMap(template, overrides);
        Map<String, Object> spec = (Map<String, Object>) merged.get("spec");
        assertEquals(3, ((Collection) spec.get("containers")).size());
        assertEquals(2, ((Collection) spec.get("volumes")).size());

        List<Map<String, Object>> containers = ((List<Map<String, Object>>) spec.get("containers"));
        assertEquals(1, containers.stream().filter(c -> c.containsValue("main")).collect(toList()).size());
        for (Map<String, Object> container : containers) {
            if (container.containsValue("main")) {
                assertNotEquals(null, container.get("image"));
                assertNotEquals(null, container.get("volumeMounts"));
            }
        }
    }
}
