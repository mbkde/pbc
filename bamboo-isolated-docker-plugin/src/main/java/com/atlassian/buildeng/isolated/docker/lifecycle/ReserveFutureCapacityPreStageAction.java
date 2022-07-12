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

package com.atlassian.buildeng.isolated.docker.lifecycle;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.chains.BuildExecution;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreStageAction;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummaryCriteria;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.bamboo.v2.build.BuildKey;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReserveFutureCapacityPreStageAction implements PreStageAction {

    private static final int MINIMUM_SUCCESS_PERCENTAGE = 50;
    private static final int MINIMUM_RESULT_COUNT = 5;
    private static final int TOTAL_RESULT_COUNT = 10;

    private static final Logger logger = LoggerFactory.getLogger(ReserveFutureCapacityPreStageAction.class);
    private final IsolatedAgentService isoService;
    private final ResultsSummaryManager resultsSummaryManager;
    private final CachedPlanManager cachedPlanManager;
    private final ContainerSizeDescriptor sizeDescriptor;

    @Inject
    public ReserveFutureCapacityPreStageAction(IsolatedAgentService isoService,
            ResultsSummaryManager resultsSummaryManager, CachedPlanManager cachedPlanManager,
            ContainerSizeDescriptor sizeDescriptor) {
        this.isoService = isoService;
        this.resultsSummaryManager = resultsSummaryManager;
        this.cachedPlanManager = cachedPlanManager;
        this.sizeDescriptor = sizeDescriptor;
    }

    @Override
    public void execute(StageExecution stageExecution) throws InterruptedException, Exception {
        Long currentStageMem = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex())
                .collect(Collectors.summingLong((Configuration value) -> value.getMemoryTotal(sizeDescriptor)));
        Long currentStageCpu = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex())
                .collect(Collectors.summingLong((Configuration value) -> value.getCPUTotal(sizeDescriptor)));
        Long nextStageMem = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1)
                .collect(Collectors.summingLong((Configuration value) -> value.getMemoryTotal(sizeDescriptor)));
        Long nextStageCpu = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1)
                .collect(Collectors.summingLong((Configuration value) -> value.getCPUTotal(sizeDescriptor)));
        long diffCpu = Math.max(0, nextStageCpu - currentStageCpu);
        long diffMem = Math.max(0, nextStageMem - currentStageMem);
        if (diffMem > 0 || diffCpu > 0) {
            long seconds = stageSuccessfulCompletionAvg(stageExecution);
            if (seconds != -1) { //for now ignore the time it takes, only care about succ/fail ratio
                BuildKey key = findBuildKey(stageExecution);
                ReserveFutureCapacityPostStageAction.storeFutureState(stageExecution, 
                        ReserveFutureCapacityPostStageAction.FutureState.RESERVED);
                logger.info("Adding future reservation for {} {} ", key, 
                        stagePBCJobResultKeys(stageExecution.getChainExecution(), stageExecution.getStageIndex()));
                isoService.reserveCapacity(key,
                    stagePBCJobResultKeys(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1),
                    diffMem, diffCpu);
            } else {
                ReserveFutureCapacityPostStageAction.storeFutureState(stageExecution,
                        ReserveFutureCapacityPostStageAction.FutureState.NOT_RESERVED);
            }
        } else {
            ReserveFutureCapacityPostStageAction.storeFutureState(stageExecution,
                    ReserveFutureCapacityPostStageAction.FutureState.NOT_APPLICABLE);
        }
    }



    static BuildKey findBuildKey(StageExecution stageExecution) {
        return stageExecution.getBuilds().stream()
                .findAny()
                .map((BuildExecution t) -> t.getBuildContext().getBuildKey())
                .orElseThrow(() -> new IllegalStateException("Stage should have at least one job"));
    }

    private Stream<Configuration> stagePBCExecutions(ChainExecution chainExecution, int stageIndex) {
        Stream<Configuration> stream = chainExecution.getStages().stream()
                .filter((StageExecution t) -> t.getStageIndex() == stageIndex)
                .flatMap((StageExecution t) -> t.getBuilds().stream())
                .map((BuildExecution t) -> AccessConfiguration.forContext(t.getBuildContext()))
                .filter((Configuration t) -> t.isEnabled());
        return stream;
    }

    static List<String> stagePBCJobResultKeys(ChainExecution chainExecution, int stageIndex) {
        return chainExecution.getStages().stream()
                .filter((StageExecution t) -> t.getStageIndex() == stageIndex)
                .flatMap((StageExecution t) -> t.getBuilds().stream())
                .filter((BuildExecution t) -> AccessConfiguration.forContext(t.getBuildContext()).isEnabled())
                .map((BuildExecution t) -> t.getBuildContext().getResultKey().getKey())
                .collect(Collectors.toList());
    }

    long stageSuccessfulCompletionAvg(StageExecution stageExecution) {
        // for each job in stage, try to collect 10 build results in current branch, if not
        // present fallback to master branch.
        // once we have these, for each job's results calculate the success ratio and avg time it takes to build.
        // reduce to get the minumum success rate and maximum avg time across jobs.
        Optional<Stats> stats = stageExecution.getBuilds().stream()
                .map((BuildExecution t) -> t.getPlanResultKey().getPlanKey())
                .map((PlanKey t) -> cachedPlanManager.getPlanByKey(t))
                .map((ImmutablePlan t) -> {
                    final ResultsSummaryCriteria criteria = new ResultsSummaryCriteria(t.getPlanKey().getKey());
                    criteria.setMaxRowCount(TOTAL_RESULT_COUNT);
                    List<ResultsSummary> summs = new ArrayList<>();
                    summs.addAll(resultsSummaryManager.getResultSummaries(criteria));
                    if (summs.size() < TOTAL_RESULT_COUNT && t.getMaster() != null) {
                        final ResultsSummaryCriteria masterCrit = new ResultsSummaryCriteria(t.getMaster().getPlanKey().getKey());
                        masterCrit.setMaxRowCount(TOTAL_RESULT_COUNT - summs.size());
                        summs.addAll(resultsSummaryManager.getResultSummaries(masterCrit));
                    }
                    return summs;
                })
                .map((List<ResultsSummary> t) -> {
                    int count = t.size();
                    long sum = 0;
                    int succCount = 0;
                    for (ResultsSummary s : t) {
                        if (BuildState.SUCCESS.equals(s.getBuildState())) {
                            succCount = succCount + 1;
                            sum = sum + s.getDuration();
                        }
                    }
                    return new Stats(count, count != 0 ? (succCount * 100) / count : 0 , succCount != 0 ? sum / succCount : 0);
                })
                .reduce((Stats t, Stats u) -> new Stats(Math.min(t.count, u.count), Math.min(t.successPercentage, u.successPercentage), Math.max(t.avgDuration, u.avgDuration)));

        logger.debug("TEST_SECONDS:" + stageExecution.getChainExecution().getPlanResultKey() + "  " + stats.toString());
        //compare collected data to minimum requirements
        if (stats.isPresent() && stats.get().count > MINIMUM_RESULT_COUNT && stats.get().successPercentage > MINIMUM_SUCCESS_PERCENTAGE) {
            return stats.get().avgDuration;
        } else {
            return -1;
        }
    }


    private class Stats {
        final int count;
        final int successPercentage;
        final long avgDuration;

        public Stats(int count, int successPerc, long avgDuration) {
            this.count = count;
            this.successPercentage = successPerc;
            this.avgDuration = avgDuration;
        }

        @Override
        public String toString() {
            return "Stats{" + "count=" + count + ", successPercentage=" + successPercentage + ", avgDuration=" + avgDuration + '}';
        }
    }
}
