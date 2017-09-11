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

import com.atlassian.buildeng.ecs.scheduling.ArnStoppedState;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.shared.AbstractWatchdogJob;
import com.atlassian.buildeng.ecs.shared.StoppedState;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECSWatchdogJob extends AbstractWatchdogJob {
    private static final Logger logger = LoggerFactory.getLogger(ECSWatchdogJob.class);

    @Override
    protected List<StoppedState> retrieveStoppedTasksByArn(List<String> arns, Map<String, Object> jobDataMap) 
            throws Exception {
        GlobalConfiguration globalConfig = getService(GlobalConfiguration.class, "globalConfiguration", jobDataMap);
        SchedulerBackend backend = getService(SchedulerBackend.class, "schedulerBackend", jobDataMap);
        return backend.checkStoppedTasks(globalConfig.getCurrentCluster(), arns).stream()
                .map((ArnStoppedState t) -> new StoppedState((t.getArn()), t.getContainerArn(), t.getReason()))
                .collect(Collectors.toList());
    }
}
