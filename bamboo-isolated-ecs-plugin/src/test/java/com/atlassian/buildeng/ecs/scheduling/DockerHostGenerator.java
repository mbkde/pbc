package com.atlassian.buildeng.ecs.scheduling;

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
        Integer registeredMemory = r.nextInt(0, 244*1024); //d2.8xlarge
        Integer remainingMemory = r.nextInt(0, registeredMemory);
        Integer registeredCpu = r.nextInt(0, 40*1024); //m4.10xlarge
        Integer remainingCpu = r.nextInt(0, registeredCpu);
        String containerInstanceArn = stringGenerator.generate(r, status);
        String instanceId = stringGenerator.generate(r, status);
        Date launchTime = new DateGenerator().generate(r, status);
        Boolean agentConnected = r.nextBoolean();
        return new DockerHost(remainingMemory, remainingCpu, registeredMemory, registeredCpu, containerInstanceArn, instanceId, launchTime, agentConnected);
    }
}