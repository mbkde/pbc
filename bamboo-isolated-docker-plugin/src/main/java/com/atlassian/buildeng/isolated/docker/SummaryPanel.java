package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.plugin.web.model.WebPanel;
import com.atlassian.plugin.webresource.Config;
import scala.collection.mutable.StringBuilder;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public class SummaryPanel implements WebPanel {
    @Override
    public String getHtml(Map<String, Object> context) {
        Boolean dockerEnabled = (Boolean) context.get(Constants.ENABLED_FOR_JOB);
        StringBuilder ret = new StringBuilder();
        if (dockerEnabled != null && dockerEnabled) {
            String dockerImage = (String) context.get(Constants.DOCKER_IMAGE);
            ret.append("<h3>Built with isolated docker</h3><br>");
            ret.append("Image used: " + dockerImage + "<br>");
        }
        return ret.toString();
    }

    @Override
    public void writeHtml(Writer writer, Map<String, Object> context) throws IOException {
        writer.append(getHtml(context));
    }
}
