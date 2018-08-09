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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler.selectHost;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import com.atlassian.event.api.EventPublisher;
import java.time.Duration;
import java.util.Map;
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
    static double percentageUtilized(List<DockerHost> freshHosts) {
        double clusterRegisteredCPU = freshHosts.stream().mapToInt(DockerHost::getRegisteredCpu).sum();
        double clusterRemainingCPU = freshHosts.stream().mapToInt(DockerHost::getRemainingCpu).sum();
        return clusterRegisteredCPU == 0 ? 1 : 1 - clusterRemainingCPU / clusterRegisteredCPU;

    }

    @Property public void percentageUtilizedValidBounds(LinkedList<@From(DockerHostGenerator.class)DockerHost> testHosts) {
        double result = percentageUtilized(testHosts);
        assertThat(result, is(both( greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(1.0))));
        // empty lists -> the cluster is fully utilized
        if (testHosts.isEmpty()) {
            assertEquals(1, result, 0.01d);
        }
    }

    @Property public void selectHostTest(LinkedList<@From(DockerHostGenerator.class)DockerHost> candidates, Integer requiredMemory, Integer requiredCpu) {
        assumeThat(requiredMemory, greaterThan(0));
        assumeThat(requiredCpu, greaterThan(0));
        Optional<DockerHost> result = selectHost(candidates, requiredMemory, requiredCpu, false);
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
        final AWSSchedulerBackend awsSchedulerBackend = new AWSSchedulerBackend();
        final EventPublisher eventPublisher = new EventPublisher() {
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
        };
        DefaultModelUpdater dmu = new DefaultModelUpdater(awsSchedulerBackend, eventPublisher);
        DockerHosts hosts = new DockerHosts(allHosts, Duration.ofDays(1), new AutoScalingGroup(), "cn");
        List<DockerHost> selectedHosts = dmu.selectToTerminate(hosts, new ModelUpdater.State(0, 0));
        if (allHosts.isEmpty()) {
            // If we have nothing to potentially terminate, we shouldn't select anything
            assertTrue(selectedHosts.isEmpty());
        } else {
            // If we have anything to potentially terminate, we shouldn't terminate everything
            assertTrue(selectedHosts.size() < allHosts.size());
        }
    }

    static class TestECSConfigurationImpl implements ECSConfiguration {

        public TestECSConfigurationImpl() {
        }

        @Override
        public String getCurrentCluster() {
            return null;
        }

        @Override
        public String getCurrentASG() {
            return null;
        }

        @Override
        public String getTaskDefinitionName() {
            return null;
        }

        @Override
        public String getLoggingDriver() {
            return null;
        }

        @Override
        public Map<String, String> getLoggingDriverOpts() {
            return null;
        }

        @Override
        public Map<String, String> getEnvVars() {
            return null;
        }

        @Override
        public ContainerSizeDescriptor getSizeDescriptor() {
            return new DefaultContainerSizeDescriptor();
        }
    }
}