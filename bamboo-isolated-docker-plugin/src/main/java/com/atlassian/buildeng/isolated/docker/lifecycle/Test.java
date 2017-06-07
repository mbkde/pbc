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
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreJobAction;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test implements PreJobAction {
    private final IsolatedAgentService isoService;

    public Test(IsolatedAgentService isoService) {
        this.isoService = isoService;
    }

    @Override
    public void execute(StageExecution stageExecution, BuildContext buildContext) {
        Stream<Configuration> stream = stageExecution.getChainExecution().getStages().stream()
                .filter((StageExecution t) -> t.getStageIndex() == stageExecution.getStageIndex() + 1)
                .flatMap((StageExecution t) -> t.getBuilds().stream())
                .map((BuildExecution t) -> AccessConfiguration.forContext(t.getBuildContext()))
                .filter((Configuration t) -> t.isEnabled());
        Long nextStageMem = stream.collect(Collectors.summingLong((Configuration value) -> value.getMemoryTotal()));
        Long nextStageCpu = stream.collect(Collectors.summingLong((Configuration value) -> value.getCPUTotal()));
        System.out.println("next stage needs:" + nextStageCpu + " " + nextStageMem);
        isoService.reserveCapacity(buildContext.getBuildKey(), stageExecution.getStageIndex(), nextStageMem, nextStageCpu);
    }

}
