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

package com.atlassian.buildeng.simple.backend.rest;


/**
 *
 * @author mkleint
 */
public class Config {
    public String apiVersion;
    public String certPath;
    public String url;
    public boolean sidekickImage;
    public String sidekick;

    public Config() {
    }

    public Config(String api, String certPath, String url, String sidekick, boolean image) {
        this.apiVersion = api;
        this.certPath = certPath;
        this.url = url;
        this.sidekick = sidekick;
        this.sidekickImage = image;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSidekickImage() {
        return sidekickImage;
    }

    public void setSidekickImage(boolean sidekickImage) {
        this.sidekickImage = sidekickImage;
    }

    public String getSidekick() {
        return sidekick;
    }

    public void setSidekick(String sidekick) {
        this.sidekick = sidekick;
    }
    
    
}
