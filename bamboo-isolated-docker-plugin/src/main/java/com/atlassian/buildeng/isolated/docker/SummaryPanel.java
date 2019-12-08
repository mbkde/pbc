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

import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.plugin.web.model.WebPanel;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("UnnecessarilyQualifiedInnerClassAccess")
public class SummaryPanel implements WebPanel {
    
    private final BuildQueueManager buildQueueManager;
    private final IsolatedAgentService detail;

    public SummaryPanel(BuildQueueManager buildQueueManager, IsolatedAgentService detail) {
        this.buildQueueManager = buildQueueManager;
        this.detail = detail;
    }
    
    @Override
    public String getHtml(Map<String, Object> context) {
        BuildResultsSummary summary = (BuildResultsSummary) context.get("resultSummary");
        Map<PlanResultKey, BuildContext> map = PlanSummaryPanel.mapKeyToBuildContext(buildQueueManager);
        BuildContext buildcontext = map.get(summary.getPlanResultKey());
        
        //when a build is queued, we derive data from the CurrentResult, not the persisted value (reruns)
        Configuration configuration = buildcontext != null ? AccessConfiguration.forContext(buildcontext) 
                : AccessConfiguration.forBuildResultSummary(summary);
        StringBuilder ret = new StringBuilder();
        if (configuration.isEnabled()) {
            ret.append("<h2>Built with Per-build Container Agent</h2>")
               .append("<dl class=\"details-list\"><dt>Image(s) used:</dt><dd>")
               .append(ConfigurationOverride.reverseRegistryOverride(configuration.getDockerImage()));
            configuration.getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
                ret.append("<br/>").append(ConfigurationOverride.reverseRegistryOverride(t.getImage()));
            });
            ret.append("</dd>");
            
            if (configuration.getAwsRole() != null) {
                ret.append("<dt>AWS IAM Role:</dt><dd>").append(configuration.getAwsRole()).append("</dd>");
            }
            
            Map<String, String> customData =
                    createCustomDataMap(buildcontext, summary);

            Map<String, URL> containerLogs = detail.getContainerLogs(configuration, customData);
            if (!containerLogs.isEmpty()) {
                ret.append("<dt>Container logs:</dt><dd>");
                ret.append(containerLogs.entrySet().stream().map((Map.Entry e) ->
                        "<a href=\"" + e.getValue().toString() + "\">" + e.getKey() + "</a>"
                ).collect(Collectors.joining(",&nbsp;&nbsp;")));
                ret.append("</dd>");
            }
            String error = buildcontext != null 
                    ? buildcontext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR) 
                    : summary.getCustomBuildData().get(Constants.RESULT_ERROR);
            if (error != null) {
                ret.append("<dt>Error:</dt><dd class=\"errorText\">")
                   .append(error)
                   .append("</dd>");
            }
            customData.entrySet().stream()
                    .filter((Map.Entry<String, String> entry) -> entry.getKey().startsWith(Constants.RESULT_PREFIX))
                    .forEach((Map.Entry<String, String> entry) -> {
                        ret.append("<dt>").append(entry.getKey().substring(Constants.RESULT_PREFIX.length())).append("</dt>");
                        ret.append("<dd>").append(entry.getValue()).append("</dd>");
                    }
            );
            ret.append("</dl>");
//TODO more information
//ret.append("Queuing time:").append(summary.getQueueDuration() / 1000).append (" seconds<br>");
        }
        return ret.toString();
    }

    static Map<String, String> createCustomDataMap(BuildContext buildcontext, BuildResultsSummary summary) {
        Map<String, String> customData =
                new HashMap<>(buildcontext != null
                        ? buildcontext.getCurrentResult().getCustomBuildData()
                        : summary.getCustomBuildData());
        customData.entrySet().removeIf((Map.Entry<String, String> t) -> !t.getKey().startsWith(Constants.RESULT_PREFIX));
        return customData;
    }

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context) throws IOException {
        writer.append(getHtml(context));
    }
}
