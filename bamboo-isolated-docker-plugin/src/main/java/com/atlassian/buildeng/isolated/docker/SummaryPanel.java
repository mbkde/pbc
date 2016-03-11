package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.plugin.web.model.WebPanel;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public class SummaryPanel implements WebPanel {
    @Override
    public String getHtml(Map<String, Object> context) {
        BuildResultsSummary summary = (BuildResultsSummary) context.get("resultSummary");
        Configuration configuration = Configuration.forBuildResultSummary(summary);
        StringBuilder ret = new StringBuilder();
        if (configuration.isEnabled()) {
            ret.append("<h2>Built with Isolated Docker</h2>")
               .append("<dl class=\"details-list\"><dt>Image used:</dt><dd>")
               .append(configuration.getDockerImage()).append("</dd>");
            String error = summary.getCustomBuildData().get(Constants.RESULT_ERROR);
            if (error != null) {
                ret.append("<dt>Error:</dt><dd>")
                   .append(error)
                   .append("</dd>");
            }
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
