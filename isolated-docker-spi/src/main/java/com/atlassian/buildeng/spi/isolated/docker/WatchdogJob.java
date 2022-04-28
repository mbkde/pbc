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

package com.atlassian.buildeng.spi.isolated.docker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.deployments.execution.DeploymentContext;
import com.atlassian.bamboo.deployments.execution.service.DeploymentExecutionService;
import com.atlassian.bamboo.deployments.results.DeploymentResult;
import com.atlassian.bamboo.deployments.results.service.DeploymentResultService;
import com.atlassian.bamboo.security.ImpersonationHelper;
import com.atlassian.bamboo.utils.BambooRunnables;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.spring.container.ContainerManager;
import java.util.Map;
import org.quartz.Job;
import org.slf4j.Logger;


public abstract class WatchdogJob implements Job {
    protected final void killBuild(
            DeploymentExecutionService deploymentExecutionService,
            DeploymentResultService deploymentResultService,
            Logger logger,
            BuildQueueManager buildQueueManager,
            CommonContext context,
            CurrentResult current
    ) {
        if (context instanceof BuildContext) {
            current.setLifeCycleState(LifeCycleState.NOT_BUILT);
            buildQueueManager.removeBuildFromQueue(context.getResultKey());
        } else if (context instanceof DeploymentContext) {
            DeploymentContext dc = (DeploymentContext) context;
            ImpersonationHelper.runWithSystemAuthority((BambooRunnables.NotThrowing) () -> {
                //without runWithSystemAuthority() this call terminates execution with a log entry only
                DeploymentResult deploymentResult = deploymentResultService.getDeploymentResult(
                        dc.getDeploymentResultId());
                if (deploymentResult != null) {
                    deploymentExecutionService.stop(deploymentResult, null);
                }
            });
        } else {
            logger.error("unknown type of CommonContext {}", context.getClass());
        }
    }

    protected final <T> T getService(Class<T> type, String serviceKey) {
        final Object obj = checkNotNull(
                ContainerManager.getComponent(serviceKey), "Expected value for key '" + serviceKey + "', found nothing."
        );
        return type.cast(obj);
    }
    
    protected final <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
        final Object obj = checkNotNull(jobDataMap.get(serviceKey),
                "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }

}
