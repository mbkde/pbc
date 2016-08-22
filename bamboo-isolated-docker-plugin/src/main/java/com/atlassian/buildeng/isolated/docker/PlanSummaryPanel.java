package com.atlassian.buildeng.isolated.docker;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.plugin.web.model.WebPanel;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public class PlanSummaryPanel implements WebPanel {
    @Override
    public String getHtml(Map<String, Object> context) {
        ChainResultsSummary summary = (ChainResultsSummary) context.get("resultSummary");
        StringBuilder ret = new StringBuilder();
        for (ResultsSummary brs : summary.getOrderedJobResultSummaries()) {
            Configuration config = Configuration.forBuildResultSummary((BuildResultsSummary) brs);
            if (config.isEnabled()) {
                String error = brs.getCustomBuildData().get(Constants.RESULT_ERROR);
                ret.append("<dt>")
                   .append(brs.getImmutablePlan().getBuildName())
                   .append("</dt><dd>")
                   .append(config.getDockerImage());
                if (error != null) {
                    ret.append("<br/><span class=\"errorText\">")
                       .append(error)
                       .append("</span>");
                }
                ret.append("</dd>");
            }
        }
        if (ret.length() > 0) {
            ret.insert(0, "<h2>Jobs built by Isolated Docker</h2><dl class=\"details-list\">");
            ret.append("</dl>");
            return ret.toString();
        }
        return "";
    }

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context) throws IOException {
        writer.append(getHtml(context));
    }
}
