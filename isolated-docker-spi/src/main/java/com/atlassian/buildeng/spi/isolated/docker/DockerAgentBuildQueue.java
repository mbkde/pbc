/*
 * Copyright 2017 Atlassian Pty Ltd.
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

import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.google.common.base.Functions;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.commons.collections4.IterableUtils;

public final class DockerAgentBuildQueue {
    public static final String BUILD_KEY = "custom.isolated.docker.buildkey";

    /**
     * the method will reliably return a list of currently scheduled PBC jobs.
     * It will only return those that have been queued AND initially processed by PBC plugins.
     * To be used by parts of implementation plugin's background scheduled jobs instead of the
     * Bamboo's own QueueManagerView.
     */
    public static Stream<CommonContext> currentlyQueued(BuildQueueManager buildQueueManager) {
        QueueManagerView<CommonContext, CommonContext> queue = QueueManagerView.newView(buildQueueManager,
                Functions.<BuildQueueManager.QueueItemView<CommonContext>>identity());

        return StreamSupport.stream(queue.getQueueView(IterableUtils.emptyIterable()).spliterator(), false)
                .map((BuildQueueManager.QueueItemView<CommonContext> t) -> t.getView())
                //this filter is crutial for BUILDENG-12837
                .filter((CommonContext t) ->
                        t.getBuildKey().getKey().equals(t.getCurrentResult().getCustomBuildData().get(BUILD_KEY)));
    }
}
