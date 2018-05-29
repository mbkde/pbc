/*
 * Copyright 2018 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.handler;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.build.docker.DockerHandler;
import com.atlassian.bamboo.build.docker.DockerHandlerProvider;
import com.atlassian.bamboo.deployments.configuration.service.EnvironmentCustomConfigService;
import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.deployments.environments.requirement.EnvironmentRequirementService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.opensymphony.xwork2.TextProvider;
import java.util.Map;

public class DockerHandlerProviderImpl implements DockerHandlerProvider<ModuleDescriptor> {

    static final String ISOLATION_TYPE = "PBC";
    
    private ModuleDescriptor moduleDescriptor;
    private final TemplateRenderer templateRenderer;
    private final EnvironmentCustomConfigService environmentCustomConfigService;
    private final EnvironmentRequirementService environmentRequirementService;
    private final WebResourceManager webResourceManager;

    /**
     * New stateless instance.
     */
    public DockerHandlerProviderImpl(TemplateRenderer templateRenderer, 
            EnvironmentCustomConfigService environmentCustomConfigService, 
            EnvironmentRequirementService environmentRequirementService,
            WebResourceManager webResourceManager) {
        this.templateRenderer = templateRenderer;
        this.environmentCustomConfigService = environmentCustomConfigService;
        this.environmentRequirementService = environmentRequirementService;
        this.webResourceManager = webResourceManager;
    }
    
    @Override
    public String getIsolationType() {
        return ISOLATION_TYPE;
    }

    @Override
    public DockerHandler getHandler(BuildDefinition bd, boolean create) {
        Configuration c = bd != null ? AccessConfiguration.forBuildDefinition(bd) 
                : ConfigurationBuilder.create("").withEnabled(false).build();
        return new DockerHandlerImpl(moduleDescriptor, webResourceManager, templateRenderer, 
                environmentCustomConfigService, environmentRequirementService, create, c);
    }

    @Override
    public DockerHandler getHandler(Environment environment, boolean create) {
        Configuration c = environment != null 
                ? AccessConfiguration.forEnvironment(environment, environmentCustomConfigService)
                : ConfigurationBuilder.create("").withEnabled(false).build();
        return new DockerHandlerImpl(moduleDescriptor, webResourceManager, templateRenderer, 
                environmentCustomConfigService, environmentRequirementService,
                create, c);
    }
    
    @Override
    public DockerHandler getHandler(Map<String, Object> webFragmentsContextMap, boolean create) {
        Configuration c = DockerHandlerImpl.createFromWebContext(webFragmentsContextMap);
        return new DockerHandlerImpl(moduleDescriptor, webResourceManager, templateRenderer, 
                environmentCustomConfigService, environmentRequirementService,
                create, c);
    }

    @Override
    public void init(ModuleDescriptor moduleDescriptor) {
        this.moduleDescriptor = moduleDescriptor;
    }

    @Override
    public String getIsolationTypeLabel(TextProvider textProvider) {
        return "Per Build Container (PBC) plugin";
    }

    
}
