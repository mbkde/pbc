package com.atlassian.buildeng.spi.isolated.docker;

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
import com.atlassian.sal.api.scheduling.PluginJob;
import com.atlassian.spring.container.ContainerManager;
import org.slf4j.Logger;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class WatchdogJob implements PluginJob {
    protected void killBuild(
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

    protected  <T> T getService(Class<T> type, String serviceKey) {
        final Object obj = checkNotNull(
                ContainerManager.getComponent(serviceKey), "Expected value for key '" + serviceKey + "', found nothing."
        );
        return type.cast(obj);
    }

    protected <T> T getService(Class<T> type, String serviceKey, Map<String, Object> jobDataMap) {
        final Object obj = checkNotNull(jobDataMap.get(serviceKey),
                "Expected value for key '" + serviceKey + "', found nothing.");
        return type.cast(obj);
    }

}
