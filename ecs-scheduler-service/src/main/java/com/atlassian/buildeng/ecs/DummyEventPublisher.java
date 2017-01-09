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

import com.atlassian.event.api.EventPublisher;
import javax.inject.Inject;

//TODO how do we do publishing, on bamboo server it's event being picked up
// by monitoring plugin and the datadog plugin pushes it to datadog as event.
public class DummyEventPublisher implements EventPublisher {

    @Inject
    public DummyEventPublisher() {
    }


    @Override
    public void publish(Object event) {
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

}
