/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.condition;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.isolated.docker.GlobalConfiguration;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import java.util.Map;
import javax.inject.Inject;

public class AwsVendorEnabledCondition implements Condition {
    @Inject
    private BandanaManager bandanaManager;

    @Override
    public void init(Map<String, String> map) throws PluginParseException {}

    @Override
    public boolean shouldDisplay(Map<String, Object> map) {
        return GlobalConfiguration.VENDOR_AWS.equals(GlobalConfiguration.getVendorWithBandana(bandanaManager));
    }
}
