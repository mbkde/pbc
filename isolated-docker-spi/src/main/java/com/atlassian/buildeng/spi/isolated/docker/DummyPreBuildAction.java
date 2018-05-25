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

import com.atlassian.bamboo.build.CustomPreBuildAction;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Just here to have the plugin included on agent classpath. Bamboo doesn't
 * include transitive plugin dependencies on the agent classpath, only those with explicit
 * extension point registration.
 */
public class DummyPreBuildAction implements CustomPreBuildAction {
    private static final Logger LOG = LoggerFactory.getLogger(DummyPreBuildAction.class);
    private BuildContext buildContext;


    private DummyPreBuildAction() {
    }

    @Override
    public ErrorCollection validate(BuildConfiguration config) {
        return new SimpleErrorCollection();
    }

    @Override
    public void init(BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @Override
    public BuildContext call() throws InterruptedException, Exception {
        return buildContext;
    }

}
