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

package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.event.BuildCanceledEvent;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.buildeng.kubernetes.exception.KubectlException;
import com.atlassian.buildeng.kubernetes.shell.JavaShellExecutor;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.event.api.EventListener;
import java.io.IOException;
import org.slf4j.LoggerFactory;


public class OnCancelledBuild {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OnCancelledBuild.class);

    private final ResultsSummaryManager resultSummaryManager;
    private final GlobalConfiguration globalConfiguration;

    public OnCancelledBuild(ResultsSummaryManager resultSummaryManager, GlobalConfiguration globalConfiguration) {
        this.resultSummaryManager = resultSummaryManager;
        this.globalConfiguration = globalConfiguration;
    }

    /**
     * Listener for canceled builds.
     *
     * @param event build canceled event from Bamboo
     */
    @EventListener
    public void onCancelledBuild(BuildCanceledEvent event) {
        Long agentId = event.getAgentId();
        if (agentId == null) {
            ResultsSummary result = resultSummaryManager.getResultsSummary(event.getPlanResultKey());
            if (result != null) {
                Configuration config = AccessConfiguration.forBuildResultSummary(result);
                if (config.isEnabled()) {
                    String podName = result
                            .getCustomBuildData()
                            .get(KubernetesIsolatedDockerImpl.RESULT_PREFIX + KubernetesIsolatedDockerImpl.NAME);
                    if (podName != null) {
                        KubernetesClient client = new KubernetesClient(globalConfiguration, new JavaShellExecutor());
                        try {
                            client.deletePod(podName);
                            logger.info("Deleted pod for cancelled build:{}", podName);
                        } catch (InterruptedException | IOException | KubectlException ex) {
                            logger.error("Failed to delete cancelled pod", ex);
                        }
                    }
                }
            }
        } else {
            // this case should be handled by the BuildCancelledEventListener class in generic support
            // a connected agent is being sent an event to commit suicide.
        }
    }
}
