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

package com.atlassian.buildeng.isolated.docker.compose;

import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.job.JobService;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.Preparable;

public class LocalExecAction extends ActionSupport implements Preparable {

    private final JobService jobService;

    private String jobKey;

    private boolean dockerIncluded = false;

    private Configuration configuration;
    private String dockerImageName;

    public LocalExecAction(JobService jobService) {
        this.jobService = jobService;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public String getJobKey() {
        return jobKey;
    }

    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }

    public boolean isDockerIncluded() {
        return dockerIncluded;
    }

    public String getDockerImageName() {
        return dockerImageName;
    }
    

    @Override
    public void prepare() throws Exception {
        System.out.println("preparing");
        if (jobKey != null) {
            ImmutableJob job = jobService.getJob(PlanKeys.getPlanKey(jobKey));
            configuration = AccessConfiguration.forJob(job);
            if (configuration.isEnabled()) {
                for (Configuration.ExtraContainer extra : configuration.getExtraContainers()) {
                    if (isDockerInDockerImage(extra.getImage())) {
                        dockerIncluded = true;
                        dockerImageName = extra.getName();
                    }
                }
            }
        }
    }

    private boolean isDockerInDockerImage(String image) {
        return image.startsWith("docker:") && image.endsWith("dind");
    }
  
}
