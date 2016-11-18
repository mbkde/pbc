package com.atlassian.buildeng.ecs.scheduling;

import com.atlassian.buildeng.ecs.GlobalConfiguration;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler.percentageUtilized;
import static com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler.selectHost;
import com.atlassian.event.api.EventPublisher;
import java.time.Duration;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

@RunWith(JUnitQuickcheck.class)
public class CyclingECSSchedulerQuickTest {
    @Property public void percentageUtilizedValidBounds(LinkedList<@From(DockerHostGenerator.class)DockerHost> testHosts) {
        double result = percentageUtilized(testHosts);
        assertThat(result, is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(1.0))));
        // empty lists -> the cluster is fully utilized
        if (testHosts.isEmpty()) {
            assertEquals(1, result, 0.01d);
        }
    }

    @Property public void selectHostTest(LinkedList<@From(DockerHostGenerator.class)DockerHost> candidates, Integer requiredMemory, Integer requiredCpu) {
        assumeThat(requiredMemory, greaterThan(0));
        assumeThat(requiredCpu, greaterThan(0));
        Optional<DockerHost> result = selectHost(candidates, requiredMemory, requiredCpu);
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

    @Property public void selectToTerminateTest(LinkedList<@From(DockerHostGenerator.class)DockerHost> allHosts) {
        CyclingECSScheduler ecsScheduler = new CyclingECSScheduler(new AWSSchedulerBackend(), new GlobalConfiguration(null,null, null, null), new EventPublisher() {
            @Override
            public void publish(Object event) {
            }

            @Override
            public void register(Object listener) {
            }

            @Override
            public void unregister(Object listener) {
            }

            @Override
            public void unregisterAll() {
            }
        });
        DockerHosts hosts = new DockerHosts(allHosts,Duration.ofDays(1));
        List<DockerHost> selectedHosts = ecsScheduler.selectToTerminate(hosts);
        if (allHosts.isEmpty()) {
            // If we have nothing to potentially terminate, we shouldn't select anything
            assertTrue(selectedHosts.isEmpty());
        } else {
            // If we have anything to potentially terminate, we shouldn't terminate everything
            assertTrue(selectedHosts.size() < allHosts.size());
        }
    }
}