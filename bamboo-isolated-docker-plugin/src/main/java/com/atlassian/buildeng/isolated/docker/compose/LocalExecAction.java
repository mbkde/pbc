/*
 * Copyright 2016 Atlassian.
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

package com.atlassian.buildeng.isolated.docker.compose;

import com.atlassian.bamboo.build.PlanResultsAction;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.opensymphony.xwork2.Preparable;

public class LocalExecAction extends PlanResultsAction implements Preparable {

//    private final ResultsSummaryManager resultsSummaryManager;

    private boolean dockerIncluded = false;

    private Configuration configuration;

//    public LocalExecAction(ResultsSummaryManager resultsSummaryManager) {
//        this.resultsSummaryManager = resultsSummaryManager;
//    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public boolean isDockerIncluded() {
        return dockerIncluded;
    }

    @Override
    public void prepare() throws Exception {
        if (getBuildKey() != null) {
//            ResultsSummary rs = resultsSummaryManager.getResultsSummary(PlanKeys.getPlanResultKey(getBuildKey(), getBuildNumber()));
            configuration = AccessConfiguration.forBuildResultSummary(resultsSummary);

            if (configuration.isEnabled()) {
                for (Configuration.ExtraContainer extra : configuration.getExtraContainers()) {
                    if (isDockerInDockerImage(extra.getImage())) {
                        dockerIncluded = true;
                    }
                }
            }
        }
    }

    private boolean isDockerInDockerImage(String image) {
        return image.startsWith("docker:") && image.endsWith("dind");
    }
  
}
