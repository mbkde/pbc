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
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DockerHosts {

    private final Collection<DockerHost> usable;
    private final Set<DockerHost> usedCandidates = new HashSet<>();

    private final List<DockerHost> freshHosts;
    private final List<DockerHost> unusedStaleHosts;
    private final Collection<DockerHost> agentDisconnected;
    private final AutoScalingGroup asg;
    private final String clusterName;

    DockerHosts(Collection<DockerHost> allHosts, Duration stalePeriod, AutoScalingGroup asg, String clusterName) {
        usable = allHosts.stream().filter((DockerHost t) -> t.getAgentConnected()).collect(Collectors.toList());
        agentDisconnected = allHosts.stream()
                .filter((DockerHost t) -> !ContainerInstanceStatus.DRAINING.toString().equals(t.getStatus()))
                .filter((DockerHost t) -> !t.getAgentConnected()).collect(Collectors.toSet());
        Map<Boolean, List<DockerHost>> partitionedHosts = partitionFreshness(usable, stalePeriod);
        freshHosts = partitionedHosts.get(true);
        unusedStaleHosts = unusedStaleInstances(partitionedHosts.get(false));
        this.asg = asg;
        this.clusterName = clusterName;
    }

    public void addUsedCandidate(DockerHost host) {
        usedCandidates.add(host);
    }

    public int getUsableSize() {
        return usable.size();
    }
    
    public Collection<DockerHost> agentDisconnected() {
        return Collections.unmodifiableCollection(agentDisconnected);
    }
    
    /**
     * all instances that actually have agent connected and have associated ec2 instance.
     */
    public Collection<DockerHost> allUsable() {
        return Collections.unmodifiableCollection(usable);
    }

    List<DockerHost> fresh() {
        return freshHosts;
    }

    public List<DockerHost> unusedStale() {
        return unusedStaleHosts;
    }

    public List<DockerHost> unusedFresh() {
        return unusedFreshInstances(freshHosts, usedCandidates);
    }

    public List<DockerHost> usedFresh() {
        List<DockerHost> usedFresh = new ArrayList<>(freshHosts);
        usedFresh.removeAll(unusedFresh());
        return usedFresh;
    }

    /**
     * Stream stale hosts not running any tasks.
     */
    List<DockerHost> unusedStaleInstances(List<DockerHost> staleHosts) {
        return staleHosts.stream().filter(DockerHost::runningNothing).collect(Collectors.toList());
    }

    List<DockerHost> unusedFreshInstances(List<DockerHost> freshHosts, Set<DockerHost> usedCandidates) {
        return freshHosts.stream().filter((DockerHost dockerHost) -> !usedCandidates.contains(dockerHost)).filter(DockerHost::runningNothing).filter(DockerHost::reachingEndOfBillingCycle).collect(Collectors.toList());
    }

    private Map<Boolean, List<DockerHost>> partitionFreshness(Collection<DockerHost> dockerHosts, Duration stalePeriod) {
        return dockerHosts.stream().collect(Collectors.partitioningBy(
                (DockerHost dockerHost) ->
                        dockerHost.isPresentInASG()
                        && dockerHost.ageMillis() < stalePeriod.toMillis()
                        && !ContainerInstanceStatus.DRAINING.toString().equals(dockerHost.getStatus())
                )
        );
    }

    AutoScalingGroup getASG() {
        return asg;
    }

    public String getClusterName() {
        return clusterName;
    }

    String getASGName() {
        return asg.getAutoScalingGroupName();
    }
}