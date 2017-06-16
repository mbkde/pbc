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

import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.InstancesSmallerThanAgentException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

public class CyclingECSScheduler implements ECSScheduler, DisposableBean {
    private final static Logger logger = LoggerFactory.getLogger(CyclingECSScheduler.class);
    private long lackingCPU = 0;
    private long lackingMemory = 0;
    private final Set<UUID> consideredRequestIdentifiers = new HashSet<>();
    @VisibleForTesting
    final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<Pair<SchedulingRequest, SchedulingCallback>> requests = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, ReserveRequest> futureReservations = new ConcurrentHashMap<>();
    
    private final SchedulerBackend schedulerBackend;
    private final ECSConfiguration globalConfiguration;
    final ModelLoader modelLoader;
    final ModelUpdater modelUpdater;

    @Inject
    public CyclingECSScheduler(SchedulerBackend schedulerBackend, ECSConfiguration globalConfiguration, 
            ModelLoader modelLoader, ModelUpdater modelUpdater) {
        this.schedulerBackend = schedulerBackend;
        this.globalConfiguration = globalConfiguration;
        this.modelLoader = modelLoader;
        this.modelUpdater = modelUpdater;
        executor.submit(new EndlessPolling());
    }

    // Select the best host to run a task with the given required resources out of a list of candidates
    // Is Nothing if there are no feasible hosts
    static Optional<DockerHost> selectHost(Collection<DockerHost> candidates, int requiredMemory, int requiredCpu, boolean demandOverflowing) {
        Comparator<DockerHost> comparator = DockerHost.compareByResourcesAndAge();
        if (demandOverflowing) {
            // when we know that there is demand overflow, we want to spread out the
            // scheduling, so always prefer the more empty ones. that way we keep on
            // rotating instances until they are all full (or equally utilized)
            comparator = comparator.reversed();
        }
        return candidates.stream()
                .filter(dockerHost -> dockerHost.canRun(requiredMemory, requiredCpu))
                .sorted(comparator)
                .findFirst();
    }


    @Override
    public void schedule(SchedulingRequest request, SchedulingCallback callback) {
        requests.add(Pair.of(request, callback));
    }

    private void processRequests(Pair<SchedulingRequest, SchedulingCallback> pair) {
        if (pair == null) return;
        SchedulingRequest request = pair.getLeft();
        String cluster = globalConfiguration.getCurrentCluster();
        String asgName = globalConfiguration.getCurrentASG();

        DockerHosts hosts;
        try {
            hosts = modelLoader.load(cluster, asgName);
        } catch (ECSException ex) {
            //mark all futures with exception.. and let the clients wait and retry..
            while (pair != null) {
                pair.getRight().handle(ex);
                pair = requests.poll();
            }
            logger.error("Cannot query cluster " + cluster + " containers", ex);
            return;
        }
        boolean someDiscarded = false;
        while (pair != null) {
            try {
                logger.debug("Processing request for {}", request);
                Optional<DockerHost> candidate = selectHost(hosts.fresh(), request.getMemory(), request.getCpu(), !consideredRequestIdentifiers.isEmpty());
                if (candidate.isPresent()) {
                    unreserveFutureCapacity(request);
                    DockerHost candidateHost = candidate.get();
                    SchedulingResult schedulingResult = schedulerBackend.schedule(candidateHost, cluster, request, globalConfiguration.getTaskDefinitionName());
                    hosts.addUsedCandidate(candidateHost);
                    candidateHost.reduceAvailableCpuBy(request.getCpu());
                    candidateHost.reduceAvailableMemoryBy(request.getMemory());
                    pair.getRight().handle(schedulingResult);
                    lackingCPU = Math.max(0, lackingCPU - request.getCpu());
                    lackingMemory = Math.max(0, lackingMemory - request.getMemory());
                    // If we hit a stage where we're able to allocate a job + our deficit is less than a single agent
                    // Clear everything out, we're probably fine
                    if (lackingCPU < Configuration.ContainerSize.SMALL.cpu() || lackingMemory < Configuration.ContainerSize.SMALL.memory()) {
                        consideredRequestIdentifiers.clear();
                        lackingCPU = 0;
                        lackingMemory = 0;
                    }
                } else {
                    if (! fitsOnAny(hosts.fresh(), request.getMemory())) {
                        //anything that wants to prevert rescheduling here needs changes in DefaultSchedulingCallback as well
                        pair.getRight().handle(new ECSException(new InstancesSmallerThanAgentException()));
                    } else {
                        // Note how much capacity we're lacking
                        // But don't double count the same request that comes through
                        if (consideredRequestIdentifiers.add(request.getIdentifier())) {
                            lackingCPU += request.getCpu();
                            lackingMemory += request.getMemory();
                        }
                        //scale up + down and set all other queued requests to null.
                        someDiscarded = true;
                        pair.getRight().handle(new ECSException("Capacity not available"));
                    }
                }
            } catch (ECSException ex) {
                logger.error("Scheduling failed", ex);
                pair.getRight().handle(ex);
            }
            pair = requests.poll();
            if (pair != null) {
                request = pair.getLeft();
            }
        }
        Pair<Long, Long> sum = sumOfFutureReservations();
        modelUpdater.updateModel(hosts, new ModelUpdater.State(lackingCPU, lackingMemory, someDiscarded, sum.getLeft(), sum.getRight()));
    }
    

    private void checkScaleDown() {
        try {
            String asgName = globalConfiguration.getCurrentASG();
            String cluster = globalConfiguration.getCurrentCluster();
            DockerHosts hosts = modelLoader.load(cluster, asgName);
            Pair<Long, Long> sum = sumOfFutureReservations();
            modelUpdater.scaleDown(hosts, new ModelUpdater.State(sum.getLeft(), sum.getRight()));
        } catch (ECSException ex) {
            logger.error("Failed to scale down", ex);
        }
    }
    

    void shutdownExecutor() {
        executor.shutdown();
    }

    @Override
    public void destroy() throws Exception {
        shutdownExecutor();
    }

    private boolean fitsOnAny(List<DockerHost> fresh, int memory) {
        return fresh.isEmpty() || fresh.stream().anyMatch((DockerHost t) -> t.getRegisteredMemory() > memory);
    }

    @Override
    public void reserveFutureCapacity(ReserveRequest req) {
        futureReservations.merge(req.getBuildKey(), req,
            (ReserveRequest oldone, ReserveRequest newone) -> oldone == null || oldone.getCreationTimestamp() > newone.getCreationTimestamp() ? newone : oldone
        );
        logger.info("Adding future reservation for " + req.getBuildKey() + " size: " + req.getMemoryReservation());
    }

    public void unreserveFutureCapacity(SchedulingRequest req) {
        if (req.getBuildKey() == null) return; //TODO remove, test path only
        futureReservations.computeIfPresent(req.getBuildKey(), (String key, ReserveRequest old) -> {
            final boolean present = old.getResultKeys().contains(req.getResultId());
            if (present) {
                logger.info("Removing reservation for " + key + " because of " + req.getResultId());
                return null;
            } else {
                return old;
            }
        });
    }

    //IMPORTANT: the cleanup constant defines the max time how long the previous stage can take in order to take
    // advantage of the pre expanded cluster.
    private static final int MINUTES_TO_KEEP_FUTURE_RES_ALIVE = 40;

    /**
     *
     * @return pair of memory, cpu reservation sums
     */
    private Pair<Long,Long>  sumOfFutureReservations() {
        long currentTime = System.currentTimeMillis();
        futureReservations.entrySet().removeIf((Map.Entry<String, ReserveRequest> t) ->
                Duration.ofMillis(currentTime - t.getValue().getCreationTimestamp()).toMinutes() > MINUTES_TO_KEEP_FUTURE_RES_ALIVE);
        return Pair.of(
                futureReservations.values().stream().mapToLong((ReserveRequest value) -> value.getMemoryReservation()).sum(),
                futureReservations.values().stream().mapToLong((ReserveRequest value) -> value.getCpuReservation()).sum());
    }

    private class EndlessPolling implements Runnable {

        public EndlessPolling() {
        }

        @Override
        public void run() {
            try {
                Pair<SchedulingRequest, SchedulingCallback> pair = requests.poll(Constants.POLLING_INTERVAL, TimeUnit.MINUTES);
                if (pair != null) {
                    processRequests(pair);
                } else {
                    checkScaleDown();
                }
            } catch (InterruptedException ex) {
                logger.info("Interrupted", ex);
            } catch (RuntimeException ex) {
                logger.error("Runtime Exception", ex);
            } catch (Throwable t) {
                logger.error("A very unexpected throwable", t);
            } finally {
                //try finally to guard against unexpected exceptions.
                executor.submit(this);
            }
        }
    }
}