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
package com.atlassian.buildeng.simple.backend;

import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.buildeng.simple.backend.rest.Config;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.HostFolderMapping;
import com.atlassian.buildeng.spi.isolated.docker.HostFolderMappingModuleDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.quartz.JobBuilder.newJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import org.quartz.Trigger;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author mkleint
 */
public class IsolatedDockerImpl implements IsolatedAgentService, LifecycleAware {
    /**
     * The environment variable to override on the agent per image
     */
    static String ENV_VAR_IMAGE = "IMAGE_ID";

    /**
     * The environment variable to override on the agent per server
     */
    static String ENV_VAR_SERVER = "BAMBOO_SERVER";
    
    /**
     * The environment variable to set the result spawning up the agent
     */
    static String ENV_VAR_RESULT_ID = "RESULT_ID";

    private static final String BUILD_DIR = "/buildeng/bamboo-agent-home/xml-data/build-dir";
    private static final String BUILD_DIR_VOLUME_NAME = "build-dir";

    private final AdministrationConfigurationAccessor admConfAccessor;
    private final Scheduler scheduler;
    private final PluginAccessor pluginAccessor;
    private final GlobalConfiguration globalConfiguration;
    private static final String PLUGIN_JOB_KEY = "DockerWatchdogJob";
    private static final long PLUGIN_JOB_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    public IsolatedDockerImpl(AdministrationConfigurationAccessor admConfAccessor, 
            Scheduler scheduler, GlobalConfiguration globalConfiguration,
            PluginAccessor pluginAccessor) {
        this.admConfAccessor = admConfAccessor;
        this.scheduler = scheduler;
        this.globalConfiguration = globalConfiguration;
        this.pluginAccessor = pluginAccessor;
    }
    
    @Override
    public void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback) {
        Configuration config = request.getConfiguration();
        String rk = request.getResultKey();
        String yaml = createComposeYaml(config, rk, admConfAccessor.getAdministrationConfiguration().getBaseUrl(), request.getUniqueIdentifier());
        System.out.println("yaml:" + yaml);
        File f;
        try {
            f = fileForUUID(request.getUniqueIdentifier().toString());
            Files.write(yaml, f, Charset.forName("UTF-8"));
            ProcessBuilder pb = new ProcessBuilder(ExecutablePathUtils.getDockerComposeBinaryPath(),  "up");
            globalConfiguration.decorateCommands(pb);
            pb.environment().put("COMPOSE_PROJECT_NAME", request.getUniqueIdentifier().toString());
            pb.environment().put("COMPOSE_FILE", f.getAbsolutePath());

            Process p = pb.inheritIO().start();
            p.waitFor();
            if (p.exitValue() == 0) {
                callback.handle(new IsolatedDockerAgentResult());
            } else {
                callback.handle(new IsolatedDockerAgentResult().withError("Failed to start, docker-compose exited with " + p.exitValue()));
            }
        } catch (IOException ex) {
            Logger.getLogger(IsolatedDockerImpl.class.getName()).log(Level.SEVERE, null, ex);
            callback.handle(new IsolatedDockerAgentException(ex));
        } catch (InterruptedException ex) {
            Logger.getLogger(IsolatedDockerImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static  File fileForUUID(String uuid) {
        return new File(System.getProperty("java.io.tmpdir"), "docker-compose-" + uuid + ".yaml");
    }

    String createComposeYaml(Configuration config, String rk, String baseUrl, UUID uuid) {
        final Map<String, Object> root = new HashMap<>();
        final Map<String, Object> services = new HashMap<>();
        final Map<String, Object> volumes = new HashMap<>();
        root.put("volumes", volumes);
        root.put("services", services);
        root.put("version", "2");
        
        Map<String, Object> buildDirVolume = new HashMap<>();
        buildDirVolume.put("driver", "local");
        volumes.put(BUILD_DIR_VOLUME_NAME, buildDirVolume);

        Map<String, String> envs = new HashMap<>();
        envs.put(ENV_VAR_IMAGE, config.getDockerImage());
        envs.put(ENV_VAR_RESULT_ID, rk);
        envs.put(ENV_VAR_SERVER, baseUrl);
        Map<String, Object> agent = new HashMap<>();
        List<String> agentVolumes = new ArrayList<>();
        agentVolumes.add(BUILD_DIR_VOLUME_NAME + ":" + BUILD_DIR);

        getHostFolderMappings().forEach((HostFolderMapping t) -> {
            agentVolumes.add(t.getHostPath() + ":" + t.getContainerPath());
        });

        agent.put("image", config.getDockerImage());
        agent.put("working_dir", "/buildeng");
        agent.put("entrypoint", "/buildeng/run-agent.sh");
        agent.put("environment", envs);
        agent.put("labels", Collections.singletonList("bamboo.uuid=" + uuid.toString()));
        agent.put("volumes", agentVolumes);
        services.put("bamboo-agent", agent);
        
        Config gc = globalConfiguration.getDockerConfig();
        if (gc.isSidekickImage()) {
            Map<String, Object> sidekick = new HashMap<>();
            String img = gc.getSidekick().trim();
            sidekick.put("image", img);
            services.put("bamboo-agent-sidekick", sidekick);
            agent.put("volumes_from", Collections.singletonList("bamboo-agent-sidekick"));
        } else {
            if (gc.getSidekick() != null) {
                String path = gc.getSidekick().trim();
                agentVolumes.add(path + ":/buildeng");
            }
        }
        
        final List<String> links = new ArrayList<>();
        
        config.getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
            Map<String, Object> toRet = new HashMap<>();
            toRet.put("image", t.getImage());
            Map<String, String> env = t.getEnvVariables().stream().collect(Collectors.toMap(
                    Configuration.EnvVariable::getName,
                    Configuration.EnvVariable::getValue));
            if (!env.isEmpty()) {
                toRet.put("environment", env);
            }
            String commands = Joiner.on(" ").join(t.getCommands());
            if (!commands.isEmpty()) {
                toRet.put("command", commands);
            }
            toRet.put("labels", Collections.singletonList("bamboo.uuid=" + uuid.toString()));
            toRet.put("volumes", Collections.singletonList(BUILD_DIR_VOLUME_NAME + ":" + BUILD_DIR));
            services.put(t.getName(), toRet);
            links.add(t.getName());
            if (isDockerInDockerImage(t.getImage())) {
                envs.put("DOCKER_HOST", "tcp://" + t.getName() + ":2375");
                toRet.put("privileged", Boolean.TRUE);
            }
        });
        if (!links.isEmpty()) {
            agent.put("links", links);
        }
        
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setCanonical(false);
        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }

    @Override
    public void onStart() {
        JobDataMap config = new JobDataMap();
        config.put("globalConfiguration", globalConfiguration);
        Trigger jobTrigger = newTrigger()
                .startNow()
                .withSchedule(simpleSchedule()
                        .withIntervalInMilliseconds(PLUGIN_JOB_INTERVAL_MILLIS)
                        .repeatForever()
                )
                .build();
        JobDetail pluginJob = newJob(DockerWatchdogJob.class)
                .withIdentity(PLUGIN_JOB_KEY)
                .usingJobData(config)
                .build();
        try {
            scheduler.scheduleJob(pluginJob, jobTrigger);
        } catch (SchedulerException e) {
            Logger.getLogger(IsolatedDockerImpl.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void onStop() {
        try {
            scheduler.unscheduleJob(triggerKey(PLUGIN_JOB_KEY));
        } catch (SchedulerException e) {
            Logger.getLogger(IsolatedDockerImpl.class.getName()).log(Level.SEVERE, null, e);
        }
    }
    
    private boolean isDockerInDockerImage(String image) {
        return image.contains("docker:") && image.endsWith("dind");
    }
    
    public List<HostFolderMapping> getHostFolderMappings() {
        return pluginAccessor.getEnabledModuleDescriptorsByClass(HostFolderMappingModuleDescriptor.class).stream()
                .map((HostFolderMappingModuleDescriptor t) -> t.getModule())
                .collect(Collectors.toList());
    }
}
