package com.atlassian.buildeng.ecs.scheduling;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DockerHosts {

    private final List<DockerHost> all;
    private final Set<DockerHost> usedCandidates = new HashSet<>();

    private final List<DockerHost> freshHosts;
    private final List<DockerHost> unusedStaleHosts;
    private final CyclingECSScheduler ecsScheduler;

    @VisibleForTesting
    public DockerHosts(List<DockerHost> allHosts, CyclingECSScheduler ecsScheduler) {
        all = allHosts;
        Map<Boolean, List<DockerHost>> partitionedHosts = ecsScheduler.partitionFreshness(allHosts);
        freshHosts = partitionedHosts.get(true);
        unusedStaleHosts = ecsScheduler.unusedStaleInstances(partitionedHosts.get(false));
        this.ecsScheduler = ecsScheduler;
    }

    public void addUsedCandidate(DockerHost host) {
        usedCandidates.add(host);
    }

    public int getSize() {
        return all.size();
    }
    
    public List<DockerHost> all() {
        return Collections.unmodifiableList(all);
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