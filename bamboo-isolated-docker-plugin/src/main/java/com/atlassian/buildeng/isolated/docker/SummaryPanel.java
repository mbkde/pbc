package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.plugin.web.model.WebPanel;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

@SuppressWarnings("UnnecessarilyQualifiedInnerClassAccess")
public class SummaryPanel implements WebPanel {
    
    private final BuildQueueManager buildQueueManager;

    public SummaryPanel(BuildQueueManager buildQueueManager) {
        this.buildQueueManager = buildQueueManager;
    }
    
    @Override
    public String getHtml(Map<String, Object> context) {
        BuildResultsSummary summary = (BuildResultsSummary) context.get("resultSummary");
        Map<PlanResultKey, BuildContext> map = PlanSummaryPanel.mapKeyToBuildContext(buildQueueManager);
        BuildContext buildcontext = map.get(summary.getPlanResultKey());
        
        //when a build is queued, we derive data from the CurrentResult, not the persisted value (reruns)
        Configuration configuration = buildcontext != null ? Configuration.forContext(buildcontext) : Configuration.forBuildResultSummary(summary);
        StringBuilder ret = new StringBuilder();
        if (configuration.isEnabled()) {
            ret.append("<h2>Built with Per-build Container Agent</h2>")
               .append("<dl class=\"details-list\"><dt>Image used:</dt><dd>")
               .append(configuration.getDockerImage()).append("</dd>");
            String error = buildcontext != null 
                    ? buildcontext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR) 
                    : summary.getCustomBuildData().get(Constants.RESULT_ERROR);
            if (error != null) {
                ret.append("<dt>Error:</dt><dd class=\"errorText\">")
                   .append(error)
                   .append("</dd>");
            }
            summary.getCustomBuildData().entrySet().stream()
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

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context) throws IOException {
        writer.append(getHtml(context));
    }
}
