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

package com.atlassian.buildeng.ecs.remote.rest;

public class Config {

    public String sidekickImage;
    public String serverUrl;
    public String awsRole;
    public boolean preemptiveScaling = false;

    public Config() {}

    public Config(String sidekickImage, String serverUrl, String awsRole) {
        this.sidekickImage = sidekickImage;
        this.serverUrl = serverUrl;
        this.awsRole = awsRole;
    }

    public String getSidekickImage() {
        return sidekickImage;
    }

    public void setSidekickImage(String sidekickImage) {
        this.sidekickImage = sidekickImage;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getAwsRole() {
        return awsRole;
    }

    public void setAwsRole(String awsRole) {
        this.awsRole = awsRole;
    }

    public boolean isPreemptiveScaling() {
        return preemptiveScaling;
    }

    public void setPreemptiveScaling(boolean preemptiveScaling) {
        this.preemptiveScaling = preemptiveScaling;
    }
}
