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
package com.atlassian.buildeng.ecs;

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.security.ImpersonationHelper;
import com.atlassian.bamboo.utils.BambooRunnables;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.scheduling.ArnStoppedState;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.shared.AbstractWatchdogJob;
import com.atlassian.buildeng.ecs.shared.StoppedState;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteFailEvent;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentRemoteSilentRetryEvent;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.RetryAgentStartupEvent;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fugue.Iterables;
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.base.Function;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mkleint
 */
public class ECSWatchdogJob extends AbstractWatchdogJob {
    private final static Logger logger = LoggerFactory.getLogger(ECSWatchdogJob.class);

    @Override
    protected List<StoppedState> retrieveStoppedTasksByArn(List<String> arns, Map<String, Object> jobDataMap) throws Exception {
        GlobalConfiguration globalConfig = getService(GlobalConfiguration.class, "globalConfiguration", jobDataMap);
        SchedulerBackend backend = getService(SchedulerBackend.class, "schedulerBackend", jobDataMap);
        return backend.checkStoppedTasks(globalConfig.getCurrentCluster(), arns).stream()
                .map((ArnStoppedState t) -> new StoppedState((t.getArn()), t.getContainerArn(), t.getReason()))
                .collect(Collectors.toList());
    }
}
