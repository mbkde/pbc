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

package com.atlassian.buildeng.isolated.docker.lifecycle;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomBuildProcessor;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.agent.ExecutableBuildAgent;
import com.atlassian.bamboo.v2.build.agent.capability.AgentContext;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for stopping the Docker-based Bamboo agent so it won't run more than one job.
 * Runs on agent and in coordination with PostJobActionImpl
 */
public class StopDockerAgentBuildProcessor implements CustomBuildProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StopDockerAgentBuildProcessor.class);

    private final AgentContext agentContext;
    private final BuildLoggerManager buildLoggerManager;
    private BuildContext buildContext;

    @Inject
    private StopDockerAgentBuildProcessor(AgentContext agentContext, BuildLoggerManager buildLoggerManager) {
        this.agentContext = agentContext;
        this.buildLoggerManager = buildLoggerManager;
    }

    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() {
        Configuration config = AccessConfiguration.forContext(buildContext);
        ExecutableBuildAgent buildAgent = agentContext.getBuildAgent();
        BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());

        if (buildAgent != null && config.isEnabled()) {
            buildLogger.addBuildLogEntry(String.format(
                    "Agent %s (id: %s) is a docker agent and will be stopped after this build (reason: "
                            + "Per-build Container feature enabled).",
                    buildAgent.getName(), buildAgent.getId()));
            stopAgent(buildLogger, buildAgent);
        }

        return buildContext;
    }

    private void stopAgent(BuildLogger buildLogger, final ExecutableBuildAgent buildAgent) {
        try {
            // Delay agent stop so all build info can be send to server
            new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TimeUnit.SECONDS.sleep(30);
                            } catch (InterruptedException e) {
                                logger.error("Error while waiting for build to complete", e);
                            }
                            buildAgent.stopNicely();
                        }
                    })
                    .start();

            // in some cases, eg. when artifact subscription has failed the execution
            // we don't get here to stop the agent.
            // the marker result custom data is here to notify the server processing
            // that we already called stopNicely();
            buildContext.getBuildResult().getCustomBuildData().put(Constants.RESULT_AGENT_KILLED_ITSELF, "true");
        } catch (RuntimeException e) {
            buildLogger.addErrorLogEntry(String.format(
                    "Failed to stop agent %s (id: %s) due to: %s. " + "Please notify Build Engineering about this. "
                            + "More information can be found in the agent's log file.",
                    buildAgent.getName(), buildAgent.getId(), e.getMessage()));
            logger.warn(
                    "Failed to stop agent {} (id: {}) due to: {}",
                    buildAgent.getName(),
                    buildAgent.getId(),
                    e.getMessage(),
                    e);
        }
    }
}
