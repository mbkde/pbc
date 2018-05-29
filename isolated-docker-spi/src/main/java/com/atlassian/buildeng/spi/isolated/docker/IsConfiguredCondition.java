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

package com.atlassian.buildeng.spi.isolated.docker;

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import java.util.Map;

/**
 * Can be used by plugins in atlassian-plugin.xml to condition a web-item module to only display
 * if the build is a PBC build.
 */
public class IsConfiguredCondition implements Condition {

    private final ResultsSummaryManager resultsSummaryManager;

    public IsConfiguredCondition(ResultsSummaryManager resultsSummaryManager) {
        this.resultsSummaryManager = resultsSummaryManager;
    }

    @Override
    public void init(Map<String, String> params) throws PluginParseException {
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        String jobKey = (String) context.get("buildKey");
        String buildNumber = (String) context.get("buildNumber");
        if (jobKey != null && buildNumber != null)  {
            int br = Integer.parseInt(buildNumber);
            ResultsSummary resultsSummary = resultsSummaryManager
                    .getResultsSummary(PlanKeys.getPlanResultKey(jobKey, br));
            Configuration config = AccessConfiguration.forBuildResultSummary(resultsSummary);
            return config.isEnabled();
        }
        return false;
    }

}
