/*
 * Copyright 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.metrics;

import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.buildeng.metrics.shared.PreJobActionImpl;

public class KubePreJobActionImpl extends PreJobActionImpl {

    private final GlobalConfiguration globalConfiguration;

    public KubePreJobActionImpl(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }


    @Override
    public void execute(StageExecution stageExecution, BuildContext buildContext) {
        super.execute(stageExecution, buildContext);
        buildContext
                .getBuildResult()
                .getCustomBuildData()
                .put(GlobalConfiguration.BANDANA_PROMETHEUS_URL, globalConfiguration.getPrometheusUrl());

    }


}
