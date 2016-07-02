package com.atlassian.buildeng.ecs.scheduling;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class DockerHosts {

    private final Collection<DockerHost> usable;
    private final Set<DockerHost> usedCandidates = new HashSet<>();

    private final List<DockerHost> freshHosts;
    private final List<DockerHost> unusedStaleHosts;
    private final CyclingECSScheduler ecsScheduler;
    private final Collection<DockerHost> agentDisconnected;

    @VisibleForTesting
    public DockerHosts(Collection<DockerHost> allHosts, CyclingECSScheduler ecsScheduler) {
        usable = allHosts.stream().filter((DockerHost t) -> t.getAgentConnected()).collect(Collectors.toList());
        agentDisconnected = allHosts.stream().filter((DockerHost t) -> !t.getAgentConnected()).collect(Collectors.toSet());
        Map<Boolean, List<DockerHost>> partitionedHosts = ecsScheduler.partitionFreshness(usable);
        freshHosts = partitionedHosts.get(true);
        unusedStaleHosts = ecsScheduler.unusedStaleInstances(partitionedHosts.get(false));
        this.ecsScheduler = ecsScheduler;
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
     * all instances that actually have agent connected and have associated ec2 instance
     * @return 
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
        return ecsScheduler.unusedFreshInstances(freshHosts, usedCandidates);
    }

    public List<DockerHost> usedFresh() {
        List<DockerHost> usedFresh = new ArrayList<>(freshHosts);
        usedFresh.removeAll(unusedFresh());
        return usedFresh;
    }

}