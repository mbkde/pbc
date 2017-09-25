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

package com.atlassian.buildeng.kubernetes.rest;

public class Config {

    public String sidekickImage;
    public String podTemplate;
    public String podLogsUrl;
    public String currentContext;

    public Config() {
    }

    public Config(String sidekickImage, String currentContext, String podTemplate, String podLogsUrl) {
        this.sidekickImage = sidekickImage;
        this.currentContext = currentContext;
        this.podTemplate = podTemplate;
        this.podLogsUrl = podLogsUrl;
    }

    public String getSidekickImage() {
        return sidekickImage;
    }

    public void setSidekickImage(String sidekickImage) {
        this.sidekickImage = sidekickImage;
    }

    public String getCurrentContext() {
        return currentContext;
    }

    public void setCurrentContext(String currentContext) {
        this.currentContext = currentContext;
    }

    public String getPodTemplate() {
        return podTemplate;
    }

    public void setPodTemplate(String podTemplate) {
        this.podTemplate = podTemplate;
    }

    public String getPodLogsUrl() {
        return podLogsUrl;
    }

    public void setPodLogsUrl(String podLogsUrl) {
        this.podLogsUrl = podLogsUrl;
    }
}