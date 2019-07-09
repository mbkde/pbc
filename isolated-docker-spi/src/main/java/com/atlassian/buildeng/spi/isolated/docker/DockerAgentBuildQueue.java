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
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager.QueueItemView;
import com.atlassian.bamboo.v2.build.queue.QueueManagerView;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class DockerAgentBuildQueue {
    public static final String BUILD_KEY = "custom.isolated.docker.buildkey";

    private static Function<QueueItemView<CommonContext>, QueueItemView<Optional<CommonContext>>> context2QueueItem =
        input -> {
            final CommonContext context = input.getView();

            //this filter is crutial for BUILDENG-12837
            final boolean isIsolated = context.getBuildKey().getKey()
                    .equals(context.getCurrentResult().getCustomBuildData().get(BUILD_KEY));
            final Optional<CommonContext> view = isIsolated ? Optional.of(context) : Optional.empty();
            return new QueueItemView<>(input.getQueuedResultKey(), view);
        };
    
    private static final LoadingCache<BuildQueueManager,
            QueueManagerView<CommonContext, Optional<CommonContext>>> cachedQueueManagerView =
                CacheBuilder.newBuilder()
                        .maximumSize(1)
                        .build(new CacheLoader<BuildQueueManager,
                                QueueManagerView<CommonContext, Optional<CommonContext>>>() {
                    @Override
                    public QueueManagerView<CommonContext, Optional<CommonContext>> load(final BuildQueueManager bqm) {
                        return QueueManagerView.newView(bqm, context2QueueItem);
                    }
                });

    private static volatile Iterable<QueueItemView<Optional<CommonContext>>> queueView = Collections.emptyList();

    /**
     * the method will reliably return a list of currently scheduled PBC jobs.
     * It will only return those that have been queued AND initially processed by PBC plugins.
     * To be used by parts of implementation plugin's background scheduled jobs instead of the
     * Bamboo's own QueueManagerView.
     */
    public static Stream<CommonContext> currentlyQueued(BuildQueueManager buildQueueManager) {
        final QueueManagerView<CommonContext, Optional<CommonContext>> queueManagerView = cachedQueueManagerView
                .getUnchecked(buildQueueManager);

        queueView = queueManagerView.getQueueView(queueView);
        return StreamSupport.stream(queueView.spliterator(), false)
                .map(QueueItemView::getView)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }
}
