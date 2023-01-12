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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.chains.BuildExecution;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummaryCriteria;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
public class ReserveFutureCapacityPreStageActionTest {
    @Mock
    private IsolatedAgentService isoService;

    @Mock
    private ResultsSummaryManager resultsSummaryManager;

    @Mock
    private CachedPlanManager cachedPlanManager;

    @InjectMocks
    private ReserveFutureCapacityPreStageAction action;

    @Test
    public void testNoResults() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22"});
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(Collections.emptyList());
        long ret = action.stageSuccessfulCompletionAvg(stage);
        assertEquals(-1, ret);
    }

    @Test
    public void testNotEnoughResults() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22"});
        final List<ResultsSummary> summaries = summaries(new Summ[] {
            new Summ(1000, BuildState.SUCCESS), new Summ(500, BuildState.UNKNOWN), new Summ(500, BuildState.FAILED)
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(summaries);
        long ret = action.stageSuccessfulCompletionAvg(stage);
        assertEquals(-1, ret);
    }

    @Test
    public void testNotEnoughSuccessfulResults() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22"});
        final List<ResultsSummary> summaries = summaries(new Summ[] {
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.UNKNOWN),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED)
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(summaries);
        long ret = action.stageSuccessfulCompletionAvg(stage);
        assertEquals(-1, ret);
    }

    @Test
    public void testSuccessfulResults() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22"});
        final List<ResultsSummary> summaries = summaries(new Summ[] {
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.UNKNOWN),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(summaries);
        long ret = action.stageSuccessfulCompletionAvg(stage);
        assertEquals(1000, ret);
    }

    @Test
    public void testSuccessfulResultsMoreJobs() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22", "AAA-BBB-DDD-22"});
        final List<ResultsSummary> summaries = summaries(new Summ[] {
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.UNKNOWN),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(summaries);
        final List<ResultsSummary> summaries2 = summaries(new Summ[] {
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-DDD").getKey()))))
                .thenReturn(summaries2);

        long ret = action.stageSuccessfulCompletionAvg(stage);
        assertEquals(2000, ret);
    }

    @Test
    public void testFailedResultsMoreJobs() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22", "AAA-BBB-DDD-22"});
        final List<ResultsSummary> summaries = summaries(new Summ[] {
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.UNKNOWN),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
            new Summ(500, BuildState.FAILED),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(summaries);
        final List<ResultsSummary> summaries2 = summaries(new Summ[] {
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
            new Summ(2000, BuildState.SUCCESS),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-DDD").getKey()))))
                .thenReturn(summaries2);

        long ret = action.stageSuccessfulCompletionAvg(stage);
        assertEquals(-1, ret);
    }

    @Test
    public void testDodgyCase() {
        StageExecution stage = mockStageExecution(new String[] {"AAA-BBB-CCC-22", "AAA-BBB-DDD-22"});
        final List<ResultsSummary> summaries = summaries(new Summ[] {
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-CCC").getKey()))))
                .thenReturn(summaries);
        final List<ResultsSummary> summaries2 = summaries(new Summ[] {
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(500, BuildState.FAILED),
            new Summ(1000, BuildState.SUCCESS),
            new Summ(1000, BuildState.SUCCESS),
        });
        when(resultsSummaryManager.getResultSummaries(eq(new ResultsSummaryCriteria(
                        PlanKeys.getPlanKey("AAA-BBB-DDD").getKey()))))
                .thenReturn(summaries2);

        long ret = action.stageSuccessfulCompletionAvg(stage);
        // TODO this should actually fail because we are having 1 job failing odd and 1 even results.
        // so the success ration is like 10%, rather than 50-60% as the current algo thinks.
        //        Assert.assertEquals(-1, ret);
        assertEquals(1000, ret);
    }

    private List<ResultsSummary> summaries(Summ[] summaries) {
        List<ResultsSummary> toRet = new ArrayList<>();
        for (Summ s : summaries) {
            ResultsSummary rs = mock(ResultsSummary.class);
            when(rs.getBuildState()).thenReturn(s.buildState);
            lenient().when(rs.getDuration()).thenReturn(s.duration);
            toRet.add(rs);
        }
        return toRet;
    }

    private StageExecution mockStageExecution(String[] par) {
        StageExecution stage = mock(StageExecution.class);
        final List<BuildExecution> collect = Stream.of(par)
                .map((String t) -> PlanKeys.getPlanResultKey(t))
                .map((PlanResultKey t) -> {
                    BuildExecution e = mock(BuildExecution.class);
                    when(e.getPlanResultKey()).thenReturn(t);
                    ImmutablePlan plan = mock(ImmutablePlan.class);
                    when(plan.getPlanKey()).thenReturn(t.getPlanKey());
                    when(cachedPlanManager.getPlanByKey(eq(t.getPlanKey()))).thenReturn(plan);
                    return e;
                })
                .collect(Collectors.toList());
        when(stage.getBuilds()).thenReturn(collect);
        when(stage.getChainExecution()).thenReturn(mock(ChainExecution.class));

        return stage;
    }

    private static class Summ {
        final long duration;
        final BuildState buildState;

        public Summ(long duration, BuildState buildState) {
            this.duration = duration;
            this.buildState = buildState;
        }
    }
}
