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

import com.atlassian.bamboo.chains.BuildExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.ChainStageResult;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostStageAction;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReserveFutureCapacityPostStageAction implements PostStageAction {
    private static final Logger logger = LoggerFactory.getLogger(ReserveFutureCapacityPostStageAction.class);

    private static final String FUTURE_RESERVE_STATE = "pbc.futureState";

    public ReserveFutureCapacityPostStageAction() {
    }

    @Override
    public void execute(ChainResultsSummary chainResultsSummary,
            ChainStageResult chainStageResult,
            StageExecution stageExecution) throws InterruptedException, Exception {
        // cleanup future reservations in case of failure.
        FutureState state = retrieveFutureState(stageExecution);
        boolean successful = chainStageResult.isSuccessful();
        switch (state) {
            case RESERVED:
                if (successful) {
                    logger.info("True-Positive prescheduling for {}", chainResultsSummary.getPlanResultKey().getKey());
                } else {
                    logger.warn("False-Positive prescheduling for {}", chainResultsSummary.getPlanResultKey().getKey());
                }
                break;
            case NOT_RESERVED:
                if (successful) {
                    logger.info("False-Negative prescheduling for {}", chainResultsSummary.getPlanResultKey().getKey());
                } else {
                    logger.warn("True-Negative prescheduling for {}", chainResultsSummary.getPlanResultKey().getKey());
                }
                break;
            case NOT_APPLICABLE:
                break;
            default:
                logger.error("unknown state for {}", chainResultsSummary.getPlanResultKey().getKey());
                break;
        }

    }

    static void storeFutureState(StageExecution stageExecution,
            ReserveFutureCapacityPostStageAction.FutureState state) {
        stageExecution
                .getBuilds()
                .stream()
                .map(BuildExecution::getBuildContext)
                .map(BuildContext::getBuildResult)
                .forEach((CurrentBuildResult t) -> {
                    t.getCustomBuildData().put(ReserveFutureCapacityPostStageAction.FUTURE_RESERVE_STATE, state.name());
                });
    }


    static FutureState retrieveFutureState(StageExecution stageExecution) {
        return stageExecution
                .getBuilds()
                .stream()
                .map(BuildExecution::getBuildContext)
                .map(BuildContext::getBuildResult)
                .map((CurrentBuildResult t) -> {
                    return t.getCustomBuildData().getOrDefault(FUTURE_RESERVE_STATE, FutureState.NOT_APPLICABLE.name());
                })
                .map(FutureState::valueOf)
                .findFirst()
                .get();
    }


    public static enum FutureState {
        RESERVED, // we have some future PBC jobs and we reserved space
        NOT_RESERVED, // we have some future PBC jobs but didn't reserve space
        NOT_APPLICABLE // we don't have future PBC jobs at all.
    }
}
