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

import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.generator.java.util.DateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import java.util.Date;
import java.util.List;

public class DockerHostGenerator extends Generator<DockerHost> {
    public DockerHostGenerator(Class<DockerHost> type) {
        super(type);
    }

    public DockerHostGenerator(List<Class<DockerHost>> types) {
        super(types);
    }

    @Override
    public DockerHost generate(SourceOfRandomness r, GenerationStatus status) {
        StringGenerator stringGenerator = new StringGenerator();
        int registeredMemory = r.nextInt(0, 244 * 1024); // d2.8xlarge
        int remainingMemory = r.nextInt(0, registeredMemory);
        int registeredCpu = r.nextInt(0, 40 * 1024); // m4.10xlarge
        int remainingCpu = r.nextInt(0, registeredCpu);
        String containerInstanceArn = stringGenerator.generate(r, status);
        String instanceId = stringGenerator.generate(r, status);
        Date launchTime = new DateGenerator().generate(r, status);
        boolean agentConnected = r.nextBoolean();
        return new DockerHost(remainingMemory,
                remainingCpu,
                registeredMemory,
                registeredCpu,
                containerInstanceArn,
                instanceId,
                ContainerInstanceStatus.ACTIVE.toString(),
                launchTime,
                agentConnected,
                "m4.10xlarge");
    }
}