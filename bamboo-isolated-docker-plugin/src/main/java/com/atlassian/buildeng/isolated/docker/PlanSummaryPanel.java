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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.DockerAgentBuildQueue;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.plugin.web.model.WebPanel;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PlanSummaryPanel implements WebPanel {

    private final BuildQueueManager buildQueueManager;
    private final IsolatedAgentService detail;

    public PlanSummaryPanel(BuildQueueManager buildQueueManager, IsolatedAgentService detail) {
        this.buildQueueManager = buildQueueManager;
        this.detail = detail;
    }

    @Override
    public String getHtml(Map<String, Object> context) {
        Map<PlanResultKey, BuildContext> map = mapKeyToBuildContext(buildQueueManager);
        ChainResultsSummary summary = (ChainResultsSummary) context.get("resultSummary");
        StringBuilder ret = new StringBuilder();
        for (ResultsSummary brs : summary.getOrderedJobResultSummaries()) {
            BuildContext buildcontext = map.get(brs.getPlanResultKey());
            //when a build is queued, we derive data from the CurrentResult, not the persisted value (reruns)
            Configuration config = buildcontext != null ? AccessConfiguration.forContext(buildcontext) :
                    AccessConfiguration.forBuildResultSummary((BuildResultsSummary) brs);
            if (config.isEnabled()) {
                String error = brs.getCustomBuildData().get(Constants.RESULT_ERROR);
                if (buildcontext != null) {
                    error = buildcontext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR);
                }
                final String planName = brs.getPlanIfExists().isPresent() ?
                        brs.getPlanIfExists().get().getBuildName() : brs.getPlanName();
                ret.append("<dt>")
                   .append(planName)
                   .append("</dt><dd>")
                   .append(config.getDockerImage());
                Map<String, String> custom = SummaryPanel.createCustomDataMap(buildcontext, (BuildResultsSummary) brs);
                Map<String, URL> containerLogs = detail.getContainerLogs(config, custom);
                if (!containerLogs.isEmpty()) {
                    ret.append("<br/>");
                    ret.append(containerLogs.entrySet().stream().map((Map.Entry e) ->
                            "<a href=\"" + e.getValue().toString() + "\">" + e.getKey() + "</a>"
                    ).collect(Collectors.joining(",&nbsp;&nbsp;")));
                    ret.append("</dd>");
                }
                if (error != null) {
                    ret.append("<br/><span class=\"errorText\">")
                       .append(error)
                       .append("</span>");
                }
                ret.append("</dd>");
            }
        }
        if (ret.length() > 0) {
            ret.insert(0, "<h2>Jobs built by Per-build Container agent</h2><dl class=\"details-list\">");
            ret.append("</dl>");
            return ret.toString();
        }
        return "";
    }

    static Map<PlanResultKey, BuildContext> mapKeyToBuildContext(BuildQueueManager buildQueueManager) {
        Map<PlanResultKey, BuildContext> map = new HashMap<>();
        DockerAgentBuildQueue.currentlyQueued(buildQueueManager).forEach((CommonContext t) -> {
            if (t instanceof BuildContext) {
                map.put(((BuildContext) t).getPlanResultKey(), (BuildContext) t);
            }
        });
        return map;
    }

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context) throws IOException {
        writer.append(getHtml(context));
    }
}
