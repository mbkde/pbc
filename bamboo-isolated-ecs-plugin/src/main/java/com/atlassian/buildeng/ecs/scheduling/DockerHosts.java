package com.atlassian.buildeng.ecs.scheduling;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@VisibleForTesting
final class DockerHosts {

    private final List<DockerHost> all;
    private final Set<DockerHost> usedCandidates = new HashSet<>();

    private final List<DockerHost> freshHosts;
    private final List<DockerHost> unusedStaleHosts;

    @VisibleForTesting
    public DockerHosts(List<DockerHost> allHosts, CyclingECSScheduler ecsScheduler) {
        this.all = allHosts;
        final Map<Boolean, List<DockerHost>> partitionedHosts = ecsScheduler.partitionFreshness(allHosts);
        freshHosts = partitionedHosts.get(true);
        unusedStaleHosts = ecsScheduler.unusedStaleInstances(partitionedHosts.get(false));
    }

    public void addUsedCandidate(DockerHost host) {
        usedCandidates.add(host);
    }

    public int getSize() {
        return all.size();
    }

    List<DockerHost> fresh() {
        return freshHosts;
    }

    public List<DockerHost> unusedStale() {
        return unusedStaleHosts;
    }

    public List<DockerHost> unusedFresh(CyclingECSScheduler ecsScheduler) {
        return ecsScheduler.unusedFreshInstances(freshHosts, usedCandidates);
    }

    public List<DockerHost> usedFresh(CyclingECSScheduler ecsScheduler) {
        List<DockerHost> usedFresh = new ArrayList<>(freshHosts);
        usedFresh.removeAll(unusedFresh(ecsScheduler));
        return usedFresh;
    }

}