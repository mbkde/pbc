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

import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentEcsDisconnectedPurgeEvent;
import com.atlassian.event.api.EventPublisher;
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultModelUpdater implements ModelUpdater {
    private final static Logger logger = LoggerFactory.getLogger(DefaultModelUpdater.class);

    private final SchedulerBackend schedulerBackend;
    private final EventPublisher eventPublisher;

    // when scaling down make sure this acount of free capacity ratio is not dropped below.
    // it should smooth out waves of scale up and down, increasing caching and effeciency/performance
    // of course somewhat bigger bill ensues
    private static final double SCALE_DOWN_FREE_CAP_MIN = 0.30;

    //under high load there are interminent reports of agents being disconnected
    //but these recover very fast, only want to actively kill instances
    // that have disconnected agent for at least the amount given.
    static final int TIMEOUT_IN_MINUTES_TO_KILL_DISCONNECTED_AGENT = 20; //20 for us to be able to debug what's going on. (5 minutes only for datadog to report the event)
    @VisibleForTesting
    final Map<DockerHost, Date> disconnectedAgentsCache = new HashMap<>();

    public DefaultModelUpdater(SchedulerBackend schedulerBackend, EventPublisher eventPublisher) {
        this.schedulerBackend = schedulerBackend;
        this.eventPublisher = eventPublisher;
    }


    @Override
    public void scaleDown(DockerHosts hosts) {
        terminateDisconnectedInstances(hosts);
        terminateInstances(selectToTerminate(hosts), hosts.getASGName(), true, hosts.getClusterName());
    }

    @Override
    public void updateModel(DockerHosts hosts, State req) {
        //see if we need to scale up or down..
        int currentSize = hosts.getUsableSize();
        int disconnectedSize = hosts.agentDisconnected().size() - terminateDisconnectedInstances(hosts);
        int desiredScaleSize = currentSize;
        if (req.isSomeDiscarded()) {
            // cpu and memory requirements in instances
            long cpuRequirements = req.getLackingCPU() / computeInstanceCPULimits(hosts.allUsable());
            long memoryRequirements = req.getLackingMemory() / computeInstanceMemoryLimits(hosts.allUsable());
            logger.info("Scaling w.r.t. this much cpu " + req.getLackingCPU());
            //if there are no unused fresh ones, scale up based on how many requests are pending, but always scale up
            //by at least one instance.
            long extraRequired = Math.max(1, Math.max(cpuRequirements, memoryRequirements));

            desiredScaleSize += extraRequired;
        }
        int terminatedCount = terminateInstances(selectToTerminate(hosts), hosts.getASGName(), true, hosts.getClusterName());
        desiredScaleSize = desiredScaleSize - terminatedCount;
        //we are reducing the currentSize by the terminated list because that's
        //what the terminateInstances method should reduce it to.
        currentSize = currentSize - terminatedCount;
        try {
            // we need to scale up while ignoring any broken containers, eg.
            // if 3 instances are borked and 2 ok, we need to scale to 6 and not 3 as the desiredScaleSize is calculated
            // up to this point.
            desiredScaleSize = desiredScaleSize + disconnectedSize;
            //never can scale beyond max capacity, will get an error then and not scale
            desiredScaleSize = Math.min(desiredScaleSize, hosts.getASG().getMaxSize());
            if (desiredScaleSize > currentSize && desiredScaleSize > hosts.getASG().getDesiredCapacity()) {
                //this is only meant to scale up!
                schedulerBackend.scaleTo(desiredScaleSize, hosts.getASGName());
            }
        } catch (ECSException ex) {
            logger.error("Scaling of " + hosts.getASGName() + " failed", ex);
        }

    }

    private int terminateDisconnectedInstances(DockerHosts hosts) {
        int oldSize = disconnectedAgentsCache.size();
        final Map<DockerHost, Date> cache = updateDisconnectedCache(disconnectedAgentsCache, hosts);
        if (!cache.isEmpty()) {
            //debugging block
            logger.warn("Hosts with disconnected agent:" + cache.size() + " " + cache.toString());
            //too chatty and datadog cannot filter it out properly
//            if (oldSize != cache.size()) {
//                eventPublisher.publish(new DockerAgentEcsDisconnectedEvent(cache.keySet()));
//            }
        }
        final List<DockerHost> selectedToKill = selectDisconnectedToKill(hosts, cache);

        if (!selectedToKill.isEmpty()) {
            //debugging block
            logger.warn("Hosts to kill with disconnected agent:" + selectedToKill.size() + " " + selectedToKill.toString());
            //TODO it's very hard to figure out what tasks were running on the instance.
            // 1. you need to get a list of task arns for (single) instance (aws api call per instance or paged per cluster)
            // 2. describe them (another aws api call)
            // 3. in the task fine container override for bamboo-agent container and in there find the
            //    environment variable value for RESULT_ID
            eventPublisher.publish(new DockerAgentEcsDisconnectedPurgeEvent(selectedToKill));
        }
        return terminateInstances(selectedToKill, hosts.getASGName(), false, hosts.getClusterName());
    }

    private List<DockerHost> selectDisconnectedToKill(DockerHosts hosts, Map<DockerHost, Date> dates) {
        return hosts.agentDisconnected().stream()
// container without agent still shows like it's running something but it's not true, all the things are doomed.
// maybe reevaluate at some point when major ecs changes arrive.
//                .filter(t -> t.runningNothing())
                .filter((DockerHost t) -> {
                    Date date = dates.get(t);
                    return date != null && (Duration.ofMillis(new Date().getTime() - date.getTime()).toMinutes() >= TIMEOUT_IN_MINUTES_TO_KILL_DISCONNECTED_AGENT);
                })
                .collect(Collectors.toList());
    }

    List<DockerHost> selectToTerminate(DockerHosts hosts) {
        List<DockerHost> toTerminate = Stream.concat(hosts.unusedStale().stream(), hosts.unusedFresh().stream())
                .collect(Collectors.toList());
        if (toTerminate.isEmpty()) {
            return toTerminate;
        }
        // If we're terminating all of our hosts (and we have any) keep one
        // around
        if (hosts.getUsableSize() == toTerminate.size() && !toTerminate.isEmpty()) {
            toTerminate.remove(0);
            return toTerminate;
        }
        //keep certain overcapacity around
        List<DockerHost> notTerminating = new ArrayList<>(hosts.allUsable());
        notTerminating.removeAll(toTerminate);
        long free = computeFreeCapacityMemory(notTerminating);
        long cap = computeMaxCapacityMemory(notTerminating);
        double freeRatio = (double)free / cap;
        while (freeRatio < SCALE_DOWN_FREE_CAP_MIN && !toTerminate.isEmpty()) {
            DockerHost host = toTerminate.remove(0);
            free = free + host.getRegisteredMemory();
            cap = cap + host.getRegisteredMemory();
            freeRatio = (double)free / cap;
        }
        return toTerminate;
    }

    //the return value has 2 possible meanings.
    // 1. how many instances we actually killed
    // 2. by how much the ASG size decreaesed
    // the current code is using it in both meanings, the asg drop is not calculated now and in case
    // of errors the return value is a lie as well.
    private int terminateInstances(List<DockerHost> toTerminate, String asgName, boolean decrementAsgSize, String clusterName) {
        if (!toTerminate.isEmpty()) {
            if (toTerminate.size() > 15) {
                //actual AWS limit is apparently 20
                logger.info("Too many instances to kill in one go ({}), killing the first 15 only.", toTerminate.size());
                toTerminate = toTerminate.subList(0, 14);
            }
            try {
                schedulerBackend.terminateAndDetachInstances(toTerminate, asgName, decrementAsgSize, clusterName);
            } catch (ECSException ex) {
                logger.error("Terminating instances failed", ex);
                return 0;
            }
        }
        return toTerminate.size();
    }

        /**
     * compute current value for available instance CPU of currently running instances
     *
     * @param hosts known current hosts
     * @return number of CPU power available
     */
    private int computeInstanceCPULimits(Collection<DockerHost> hosts) {
        //we settle on minimum as that's the safer option here, better to scale faster than slower.
        //the alternative is to perform more checks with the asg/launchconfiguration in aws to see what
        // the current instance size is in launchconfig
        OptionalInt minCpu = hosts.stream().mapToInt((DockerHost value) -> value.getRegisteredCpu()).min();
        //if no values found (we have nothing in our cluster, go with arbitrary value until something starts up.
        //current arbitrary values based on "m4.4xlarge"
        return minCpu.orElse(ECSInstance.DEFAULT_INSTANCE.getCpu());
    }


    /**
     * compute current value for available instance memory of currently running instances
     *
     * @param hosts known current hosts
     * @return number of memory available
     */
    private int computeInstanceMemoryLimits(Collection<DockerHost> hosts) {
        //we settle on minimum as that's the safer option here, better to scale faster than slower.
        //the alternative is to perform more checks with the asg/launchconfiguration in aws to see what
        // the current instance size is in launchconfig
        OptionalInt minMemory = hosts.stream().mapToInt((DockerHost value) -> value.getRegisteredMemory()).min();
        //if no values found (we have nothing in our cluster, go with arbitrary value until something starts up.
        //current arbitrary values based on "m4.4xlarge"
        return minMemory.orElse(ECSInstance.DEFAULT_INSTANCE.getMemory());
    }

    private long computeMaxCapacityMemory(Collection<DockerHost> hosts) {
        return hosts.stream().mapToLong((DockerHost value) -> (long)value.getRegisteredMemory()).sum();
    }

    private long computeFreeCapacityMemory(Collection<DockerHost> hosts) {
        return hosts.stream().mapToLong((DockerHost value) -> (long)value.getRemainingMemory()).sum();
    }
    
    /**
     *   update the cache with times of first report for disconnected agent. Remove those that recovered
     * , add new incidents.
     */
    private Map<DockerHost, Date> updateDisconnectedCache(Map<DockerHost, Date> cache, DockerHosts hosts) {
        cache.keySet().retainAll(hosts.agentDisconnected());
        hosts.agentDisconnected().forEach((DockerHost t) -> {
            Date date = cache.get(t);
            if (date == null) {
                cache.put(t, new Date());
            }
        });
        return cache;
    }


   
}
