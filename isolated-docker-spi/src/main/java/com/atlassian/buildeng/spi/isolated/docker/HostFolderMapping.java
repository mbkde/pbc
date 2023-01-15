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

/**
 * When implemented and registered by plugins, the backend plugins will include
 * host instance folder in the agent container at given path.
 * Implementations to be registered in atlassian-plugin.xml file, eg.
 * <code>
 * &lt;hostFolderMapping key="metrics" class="com.atlassian.buildeng.ecs.metrics.MetricHostFolderMapping"&gt;
 * &lt;/hostFolderMapping&gt;
 * </code>
 *
 * @author mkleint
 */
public interface HostFolderMapping {

    String getVolumeName();

    String getHostPath();

    String getContainerPath();
}
