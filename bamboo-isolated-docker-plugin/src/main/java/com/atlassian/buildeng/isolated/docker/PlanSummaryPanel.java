package com.atlassian.buildeng.isolated.docker;

import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.plugin.web.model.WebPanel;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PlanSummaryPanel implements WebPanel {
    
    private final BuildQueueManager buildQueueManager;

    public PlanSummaryPanel(BuildQueueManager buildQueueManager) {
        this.buildQueueManager = buildQueueManager;
    }
    
    @Override
    public String getHtml(Map<String, Object> context) {
        Map<PlanResultKey, BuildContext> map = mapKeyToBuildContext(buildQueueManager);
        ChainResultsSummary summary = (ChainResultsSummary) context.get("resultSummary");
        StringBuilder ret = new StringBuilder();
        for (ResultsSummary brs : summary.getOrderedJobResultSummaries()) {
            BuildContext buildcontext = map.get(brs.getPlanResultKey());
            //when a build is queued, we derive data from the CurrentResult, not the persisted value (reruns)
            Configuration config = buildcontext != null ? AccessConfiguration.forContext(buildcontext) : AccessConfiguration.forBuildResultSummary((BuildResultsSummary) brs);
            if (config.isEnabled()) {
                String error = brs.getCustomBuildData().get(Constants.RESULT_ERROR);
                if (buildcontext != null) {
                    error = buildcontext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR);
                }
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
            ret.insert(0, "<h2>Jobs built by Per-build Container agent</h2><dl class=\"details-list\">");
            ret.append("</dl>");
            return ret.toString();
        }
        return "";
    }

    static Map<PlanResultKey, BuildContext> mapKeyToBuildContext(BuildQueueManager buildQueueManager) {
        QueueManagerView<CommonContext, CommonContext> view = QueueManagerView.newView(buildQueueManager, (BuildQueueManager.QueueItemView<CommonContext> input) -> input);
        Map<PlanResultKey, BuildContext> map = new HashMap<>();
        view.getQueueView(Collections.emptyList()).forEach((BuildQueueManager.QueueItemView<CommonContext> t) -> {
            if (t.getView() instanceof BuildContext) {
                map.put(((BuildContext)t.getView()).getPlanResultKey(), (BuildContext)t.getView());
            }
        });
        return map;
    }

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context) throws IOException {
        writer.append(getHtml(context));
    }
}
