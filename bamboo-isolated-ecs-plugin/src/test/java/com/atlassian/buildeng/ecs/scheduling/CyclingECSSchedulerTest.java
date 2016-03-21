package com.atlassian.buildeng.ecs.scheduling;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class CyclingECSSchedulerTest {
    @Property public void percentageUtilizedValidBounds(@ForAll LinkedList<@From(DockerHostGenerator.class)DockerHost> testHosts) {
        double result = CyclingECSScheduler.percentageUtilized(testHosts);
        assertTrue(result <= 1.0 && result >= 0.0);
        // empty lists -> the cluster is fully utilized
        if (testHosts.size() == 0) {
            assertTrue(result == 1);
        }
    }

    @Property public void selectHostTest(@ForAll LinkedList<@From(DockerHostGenerator.class)DockerHost> candidates, Integer requiredMemory, Integer requiredCpu) {
        Optional<DockerHost> result = CyclingECSScheduler.selectHost(candidates, requiredMemory, requiredCpu);
        if (result.isPresent()) {
            DockerHost candidate = result.get();
            assertTrue(candidate.canRun(requiredMemory, requiredCpu));
            for (DockerHost dockerHost: candidates) {
                assertTrue(candidate.getRemainingMemory() <= dockerHost.getRemainingMemory());
            }
        } else {
            for (DockerHost dockerHost: candidates) {
                assertFalse(dockerHost.canRun(requiredMemory, requiredCpu));
            }
        }
    }
}