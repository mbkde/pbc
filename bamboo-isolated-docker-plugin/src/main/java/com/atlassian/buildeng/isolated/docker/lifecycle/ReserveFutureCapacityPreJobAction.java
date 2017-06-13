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
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreJobAction;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReserveFutureCapacityPreJobAction implements PreJobAction {
    private final static Logger logger = LoggerFactory.getLogger(ReserveFutureCapacityPreJobAction.class);
    private final IsolatedAgentService isoService;

    public ReserveFutureCapacityPreJobAction(IsolatedAgentService isoService) {
        this.isoService = isoService;
    }

    @Override
    public void execute(StageExecution stageExecution, BuildContext buildContext) {
        //set the buildKey in customData because we are not having a way to retrieve it in postJobAction otherwise
        buildContext.getBuildResult().getCustomBuildData().put(PostJobActionImpl.PBCBUILD_KEY, buildContext.getBuildKey().getKey());

        Long currentStageMem = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex()).collect(Collectors.summingLong((Configuration value) -> value.getMemoryTotal()));
        Long currentStageCpu = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex()).collect(Collectors.summingLong((Configuration value) -> value.getCPUTotal()));
        logger.debug("current stage needs:" + currentStageCpu + " " + currentStageMem);
        Long nextStageMem = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1).collect(Collectors.summingLong((Configuration value) -> value.getMemoryTotal()));
        Long nextStageCpu = stagePBCExecutions(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1).collect(Collectors.summingLong((Configuration value) -> value.getCPUTotal()));
        logger.debug("next stage needs:" + nextStageCpu + " " + nextStageMem);
        long diffCpu = Math.max(0, nextStageCpu - currentStageCpu);
        long diffMem = Math.max(0, nextStageMem - currentStageMem);
        logger.info("overhead:" + diffCpu + " " + diffMem);
        if (diffMem > 0 || diffCpu > 0) {
            isoService.reserveCapacity(buildContext.getBuildKey(), 
                    stagePBCJobResultKeys(stageExecution.getChainExecution(), stageExecution.getStageIndex() + 1),
                    diffMem, diffCpu);
        }
    }


    static Stream<Configuration> stagePBCExecutions(ChainExecution chainExecution, int stageIndex) {
        Stream<Configuration> stream = chainExecution.getStages().stream()
                .filter((StageExecution t) -> t.getStageIndex() == stageIndex)
                .flatMap((StageExecution t) -> t.getBuilds().stream())
                .map((BuildExecution t) -> AccessConfiguration.forContext(t.getBuildContext()))
                .filter((Configuration t) -> t.isEnabled());
        return stream;
    }

    static  List<String> stagePBCJobResultKeys(ChainExecution chainExecution, int stageIndex) {
        return chainExecution.getStages().stream()
                .filter((StageExecution t) -> t.getStageIndex() == stageIndex)
                .flatMap((StageExecution t) -> t.getBuilds().stream())
                .filter((BuildExecution t) -> AccessConfiguration.forContext(t.getBuildContext()).isEnabled())
                .map((BuildExecution t) -> t.getBuildContext().getResultKey().getKey())
                .collect(Collectors.toList());
    }
}
