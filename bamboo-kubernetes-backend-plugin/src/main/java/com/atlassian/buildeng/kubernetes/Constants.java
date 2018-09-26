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

package com.atlassian.buildeng.kubernetes;

public interface Constants {

    /**
     * name of system property that denotes the path to kubectl binary.
     */
    String KUBECTL_PATH_PROPERTY = "pbc.kubectl.path";
    
    String KUBECTL_EXECUTABLE = System.getProperty(KUBECTL_PATH_PROPERTY, "kubectl");
    
    /**
     * name of bamboo server property
     * that denotes what storage driver to pass to the docker-in-docker daemon.
     * With DinD it's important to keep storage drivers in sync for inner and outer daemon.
     */
    String STORAGE_DRIVER_PROPERTY = "pbc.dind.storage.driver";
    
    /**
     * the storage driver used with docker on the worker nodes in kubernetes. For docker-in-docker to work
     * we need to use this one in place of the default vfs that is terribly slow.
     */
    String STORAGE_DRIVER = System.getProperty(STORAGE_DRIVER_PROPERTY, "overlay2");
    
    /**
     * name of system property that denotes global options to pass to kubectl.
     */
    String KUBECTL_GLOBALOPTIONS_PROPERTY = "pbc.kubectl.options";
    
    /**
     * name of system property that denotes how long the delete opeation timeout should take.
     */
    String KUBECTL_DELETE_TIMEOUT_PROPERTY = "pbc.kubectl.options";
    
    /**
     * Global options to use for every request.
     */
    String KUBECTL_GLOBAL_OPTIONS = System.getProperty(KUBECTL_GLOBALOPTIONS_PROPERTY, "--request-timeout=5m");
    
    /**
     * After what amount of time should kubeclt timeout deletes. Format of --timeout switch (1s, 2m, 1h).
     */
    String KUBECTL_DELETE_TIMEOUT = System.getProperty(KUBECTL_DELETE_TIMEOUT_PROPERTY, "2m");
    
}
