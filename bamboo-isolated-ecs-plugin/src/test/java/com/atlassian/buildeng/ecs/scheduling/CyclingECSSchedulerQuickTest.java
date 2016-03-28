package com.atlassian.buildeng.ecs.scheduling;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.Optional;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(JUnitQuickcheck.class)
public class CyclingECSSchedulerQuickTest {
    @Property public void percentageUtilizedValidBounds(@ForAll LinkedList<@From(DockerHostGenerator.class)DockerHost> testHosts) {
        double result = CyclingECSScheduler.percentageUtilized(testHosts);
        assertThat(result, is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(1.0))));
        // empty lists -> the cluster is fully utilized
        if (testHosts.isEmpty()) {
            assertEquals(1, result, 0.01d);
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