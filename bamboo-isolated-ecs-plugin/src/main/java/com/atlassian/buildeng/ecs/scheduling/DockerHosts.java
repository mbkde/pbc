package com.atlassian.buildeng.ecs.scheduling;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;

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
    private final Collection<DockerHost> agentDisconnected;

    @VisibleForTesting
    public DockerHosts(Collection<DockerHost> allHosts, Duration stalePeriod) {
        usable = allHosts.stream().filter((DockerHost t) -> t.getAgentConnected()).collect(Collectors.toList());
        agentDisconnected = allHosts.stream().filter((DockerHost t) -> !t.getAgentConnected()).collect(Collectors.toSet());
        Map<Boolean, List<DockerHost>> partitionedHosts = partitionFreshness(usable, stalePeriod);
        freshHosts = partitionedHosts.get(true);
        unusedStaleHosts = unusedStaleInstances(partitionedHosts.get(false));
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
        return unusedFreshInstances(freshHosts, usedCandidates);
    }

    public List<DockerHost> usedFresh() {
        List<DockerHost> usedFresh = new ArrayList<>(freshHosts);
        usedFresh.removeAll(unusedFresh());
        return usedFresh;
    }

    /**
     * Stream stale hosts not running any tasks
     */
    List<DockerHost> unusedStaleInstances(List<DockerHost> staleHosts) {
        return staleHosts.stream().filter(DockerHost::runningNothing).collect(Collectors.toList());
    }

    List<DockerHost> unusedFreshInstances(List<DockerHost> freshHosts, Set<DockerHost> usedCandidates) {
        return freshHosts.stream().filter((DockerHost dockerHost) -> !usedCandidates.contains(dockerHost)).filter(DockerHost::runningNothing).filter(DockerHost::inSecondHalfOfBillingCycle).collect(Collectors.toList());
    }

    private Map<Boolean, List<DockerHost>> partitionFreshness(Collection<DockerHost> dockerHosts, Duration stalePeriod) {
        // Java pls
        return dockerHosts.stream().collect(Collectors.partitioningBy((DockerHost dockerHost) -> dockerHost.ageMillis() < stalePeriod.toMillis()));
    }

}