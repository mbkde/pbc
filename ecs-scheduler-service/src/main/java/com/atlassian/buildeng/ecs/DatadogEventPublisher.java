/*
 * Copyright 2016 Atlassian.
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

import com.atlassian.event.api.EventPublisher;
import com.timgroup.statsd.Event;
import com.timgroup.statsd.NonBlockingStatsDClient;
import java.io.Closeable;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;

public class DatadogEventPublisher implements EventPublisher, Closeable {
    static final String DATADOG_HOST = "DATADOG_HOST";
    static final String DATADOG_PORT = "DATADOG_PORT";
    //TODO what is the prefix good for
    private static final String PREFIX = "prefix";

    private final com.timgroup.statsd.NonBlockingStatsDClient client;

    @Inject
    public DatadogEventPublisher(@Named(DATADOG_HOST) String hostname, @Named(DATADOG_PORT) int port) {
        this.client = new NonBlockingStatsDClient(PREFIX, hostname, port);
    }

    @Override
    public void publish(Object event) {
        Event e = Event.builder()
                .withTitle(event.getClass().getName())
                .withText(event.toString())
                .withPriority(Event.Priority.LOW)
                .withAlertType(Event.AlertType.WARNING)
                .build();
        client.recordEvent(e, "pbc");
    }

    @Override
    public void register(Object listener) {
    }

    @Override
    public void unregister(Object listener) {
    }

    @Override
    public void unregisterAll() {
    }

    @Override
    public void close() throws IOException {
        client.stop();
    }

}
