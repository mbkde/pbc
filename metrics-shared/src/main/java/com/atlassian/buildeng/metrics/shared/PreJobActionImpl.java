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

package com.atlassian.buildeng.metrics.shared;

import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreJobAction;
import com.atlassian.bamboo.security.SecureTokenService;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.spring.container.ContainerManager;
import org.apache.log4j.Logger;

public class PreJobActionImpl implements PreJobAction {
    private static final Logger log = Logger.getLogger(PreJobActionImpl.class);
    public static final String SECURE_TOKEN = "secureToken";

    public PreJobActionImpl() {
    }

    @Override
    public void execute(StageExecution stageExecution, BuildContext buildContext) {
        //secureTokenService not available for plugins via injection but still used by ArtifactDownloaderRuntimeDataProvider in plugin.
        SecureTokenService secureTokenService = ContainerManager.getComponent("secureTokenService", SecureTokenService.class);
        buildContext.getBuildResult().getCustomBuildData().put(
                SECURE_TOKEN, secureTokenService.generate(buildContext.getBuildKey()).getToken()
        );
    }

}
