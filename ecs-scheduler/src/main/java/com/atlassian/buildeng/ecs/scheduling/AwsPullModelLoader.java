/*
 * Copyright 2017 Atlassian Pty Ltd.
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
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.logs.AwsLogs;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsStaleAsgInstanceEvent;
import com.atlassian.event.api.EventPublisher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AwsPullModelLoader implements ModelLoader {
    private final static Logger logger = LoggerFactory.getLogger(AwsPullModelLoader.class);
    private final SchedulerBackend schedulerBackend;
    private final EventPublisher eventPublisher;
    private final ECSConfiguration globalConfiguration;

    private final Duration stalePeriod;
    static final Duration DEFAULT_STALE_PERIOD = Duration.ofDays(7); // One (1) week

    //keep a list of asg instanceids that we reported as sad
    final List<String> reportedLonelyAsgInstances = new ArrayList<>();
    //grace period in minutes since launch
    // 5 might be too radical, but I haven't found any stats on what this the mean/average or 95percentile time for ec2 instance startup
    // a random poke at a single instance suggests something above one minute for startup on staging-bamboo.
    // but that can be significantly variable based on general state of AWS.
    // AWS Console - EC2 Launch time - December 16, 2016 at 3:44:35 PM UTC+11
    // AWS Console - ASG activity hisotry - Start 2016 December 16 15:44:36 UTC+11 -> End 2016 December 16 15:45:09 UTC+11
    // Cloud-init v. 0.7.6 finished at Fri, 16 Dec 2016 04:45:49 +0000. Datasource DataSourceEc2.  Up 50.12 seconds
    private static final int ASG_MISSING_IN_CLUSTER_GRACE_PERIOD = 5;

    @Inject
    public AwsPullModelLoader(SchedulerBackend schedulerBackend, EventPublisher eventPublisher, ECSConfiguration globalConfiguration) {
        this.schedulerBackend = schedulerBackend;
        this.eventPublisher = eventPublisher;
        stalePeriod = DEFAULT_STALE_PERIOD;
        this.globalConfiguration = globalConfiguration;
    }

    @Override
    public DockerHosts load(String clusterName, String asgName) throws ECSException {
        AutoScalingGroup asg = schedulerBackend.describeAutoScalingGroup(asgName);
        checkSuspendedProcesses(asg);
        return loadHosts(clusterName, asg);
    }

    //AZRebalance kills running agents, we need to suspend it.
    //not possible to do via terraform now, let's do explicitly from the plugin.
    private void checkSuspendedProcesses(AutoScalingGroup asg) throws ECSException {
        if (asg.getSuspendedProcesses() == null ||
            !asg.getSuspendedProcesses().stream()
                    .map((SuspendedProcess t) -> t.getProcessName())
                    //it's a pity aws lib doesn't have these as constants or enums
                    .filter((String t) -> "AZRebalance".equals(t))
                    .findAny().isPresent())
        {
            schedulerBackend.suspendProcess(asg.getAutoScalingGroupName(), "AZRebalance");
        }
    }

    DockerHosts loadHosts(String cluster, AutoScalingGroup asg) throws ECSException {
        //this can take time (network) and in the meantime other requests can accumulate.
        Map<String, ContainerInstance> containerInstances = schedulerBackend.getClusterContainerInstances(cluster).stream().collect(Collectors.toMap(ContainerInstance::getEc2InstanceId, Function.identity()));
        // We need these as there is potentially a disparity between instances with container instances registered
        // in the cluster and instances which are part of the ASG. Since we detach unneeded instances from the ASG
        // then terminate them, if the cluster still reports the instance as connected we might assign a task to
        // the instance, which will soon terminate. This leads to sad builds, so we intersect the instances reported
        // from both ECS and ASG
        Set<String> asgInstances = asg.getInstances().stream().map(x -> x.getInstanceId()).collect(Collectors.toSet());
        Set<String> allIds = new HashSet<>();
        allIds.addAll(asgInstances);
        allIds.addAll(containerInstances.keySet());
        Map<String, Instance> instances = schedulerBackend.getInstances(allIds).stream().collect(Collectors.toMap(Instance::getInstanceId, Function.identity()));

        Map<String, DockerHost> dockerHosts = new HashMap<>();
        containerInstances.forEach((String t, ContainerInstance u) -> {
            Instance ec2 = instances.get(t);
            if (ec2 != null) {
                try {
                    dockerHosts.put(t, new DockerHost(u, ec2, asgInstances.contains(t)));
                } catch (ECSException ex) {
                    logger.error("Skipping incomplete docker host", ex);
                }
            }
        });
        //sometimes asg instances get stuck on startup and never reach ecs, report on it.
        Set<String> lonelyAsgInstances = new HashSet<>(asgInstances);
        lonelyAsgInstances.removeAll(containerInstances.keySet());
        if (!lonelyAsgInstances.isEmpty()) {
            lonelyAsgInstances.stream()
                    .filter((String t) -> {
                        Instance ec2 = instances.get(t);
                        if (ec2 != null) {
                            final long lifespan = new Date().getTime() - ec2.getLaunchTime().getTime();
                            return Duration.ofMinutes(ASG_MISSING_IN_CLUSTER_GRACE_PERIOD).toMillis() < lifespan
                                    && Duration.ofMinutes(60 - Constants.MINUTES_BEFORE_BILLING_CYCLE).toMillis() > lifespan;
                        }
                        return false;
                    })
                    .filter((String t) -> !reportedLonelyAsgInstances.contains(t))
                    .forEach((String t) -> {
                        eventPublisher.publish(new DockerAgentEcsStaleAsgInstanceEvent(t));
                        reportedLonelyAsgInstances.add(t);
                        if (reportedLonelyAsgInstances.size() > 50) { //random number to keep the list from growing indefinitely
                            reportedLonelyAsgInstances.remove(0);
                        }
                        AwsLogs.logEC2InstanceOutputToCloudwatch(t, globalConfiguration);
                        try {
                            schedulerBackend.terminateInstances(Collections.<String>singletonList(t));
                        } catch (ECSException e) {
                            logger.warn("Failed to terminate instance " + t, e);
                        }
                    });
            logger.warn("Scheduler got different lengths for instances ({}) and container instances ({})", asgInstances.size(), containerInstances.size());
        }
        return new DockerHosts(dockerHosts.values(), stalePeriod, asg, cluster);
    }


}
