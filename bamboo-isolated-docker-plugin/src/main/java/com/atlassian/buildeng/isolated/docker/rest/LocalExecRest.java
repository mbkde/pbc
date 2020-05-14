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

package com.atlassian.buildeng.isolated.docker.rest;

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Path("/localExec")
public class LocalExecRest {

    private final CachedPlanManager cpm;
    private final ContainerSizeDescriptor sizeDescriptor;

    public LocalExecRest(CachedPlanManager cpm, ContainerSizeDescriptor sizeDescriptor) {
        this.cpm = cpm;
        this.sizeDescriptor = sizeDescriptor;
    }

    @GET
    @Path("{jobKey}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDockerCompose(@PathParam("jobKey") String jobKey,
            @DefaultValue("false") @QueryParam("dind") boolean useDockerInDocker,
            @DefaultValue("false") @QueryParam("mavenLocal") boolean mavenLocalRepo,
            @DefaultValue("false") @QueryParam("reservations") boolean reservations) {
        if (jobKey == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("jobKey query parameter not defined").build();
        }
        PlanKey key = PlanKeys.getPlanKey(jobKey);
        ImmutablePlan job = cpm.getPlanByKey(key);
        if (job == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Job not found").build();
        }
        ImmutableJob jb = Narrow.downTo(job, ImmutableJob.class);
        if (jb == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Not a Job Key").build();
        }
        Configuration conf = AccessConfiguration.forJob(jb);
        if (conf != null) {
            return Response.ok(createLocalExecDockerCompose(conf, useDockerInDocker, 
                    mavenLocalRepo, reservations, jobKey)).build();
        }
        return Response.status(Response.Status.NO_CONTENT).build();

    }

    private String createLocalExecDockerCompose(Configuration conf, boolean useDockerInDocker,
            boolean mavenLocalRepo, boolean reservations, String jobKey) {
        final Map<String, Object> root = new LinkedHashMap<>();
        final Map<String, Object> services = new LinkedHashMap<>();
        root.put("version", "2");
        root.put("services", services);

        Map<String, Object> bambooAgent = new LinkedHashMap<>();
        services.put("bamboo-agent", bambooAgent);
        bambooAgent.put("image", conf.getDockerImage());
        final String workingDir = "/buildeng/bamboo-agent-home/xml-data/build-dir/" + jobKey;
        bambooAgent.put("working_dir", workingDir);
        bambooAgent.put("entrypoint", "/usr/bin/tail");
        bambooAgent.put("command", "-f /dev/null");
        List<String> bambooAgentVolumes = new ArrayList<>();
        List<String> bambooAgentEnvVars = new ArrayList<>();
        List<String> bambooAgentLinks = new ArrayList<>();
        bambooAgent.put("volumes", bambooAgentVolumes);
        bambooAgentVolumes.add(".:" + workingDir);
        if (reservations) {
            bambooAgent.put("mem_limit", "" + sizeDescriptor.getMemory(conf.getSize()) + "m");
        }
        if (mavenLocalRepo) {
            bambooAgentVolumes.add("~/.m2/:/root/.m2/");
        } else {
            bambooAgentVolumes.add("~/.m2/settings.xml:/root/.m2/settings.xml");
            bambooAgentVolumes.add("~/.m2/settings-security.xml:/root/.m2/settings-security.xml");
        }
        bambooAgentVolumes.add("~/.docker/config.json:/root/.docker/config.json");
        bambooAgentVolumes.add("~/.ssh:/root/.ssh");
        bambooAgentVolumes.add("~/.gradle/gradle.properties:/root/.gradle/gradle.properties");
        bambooAgentVolumes.add("~/.gnupg/:/root/.gnupg/");
        bambooAgentVolumes.add("~/.npmrc:/root/.npmrc");
        bambooAgentVolumes.add("~/.netrc:/root/.netrc");

        bambooAgent.put("labels", Collections.singletonMap("bamboo.job", jobKey));


        conf.getExtraContainers().stream().forEach((Configuration.ExtraContainer t) -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("image", t.getImage());
            if (isDockerInDockerImage(t.getImage())) {
                if (useDockerInDocker) {
                    bambooAgentEnvVars.add("DOCKER_HOST=tcp://" + t.getName() + ":2375");
                    extra.put("privileged", true);
                } else {
                    bambooAgentVolumes.add("/var/run/docker.sock:/var/run/docker.sock");
                    return;
                }
            }
            if (reservations) {
                //is there a point in cpu reservation?
                extra.put("mem_limit", "" + sizeDescriptor.getMemory(t.getExtraSize()) + "m");
            }

            services.put(t.getName(), extra);
            bambooAgentLinks.add(t.getName());
            if (!t.getCommands().isEmpty()) {
                extra.put("command", t.getCommands());
            }
            if (!t.getEnvVariables().isEmpty()) {
                extra.put("environment", t.getEnvVariables().stream()
                        .map((Configuration.EnvVariable t1) -> t1.getName() + "=" + t1.getValue())
                        .collect(Collectors.toList()));
            }

            extra.put("volumes", Collections.singletonList(".:" + workingDir));
            extra.put("labels", Collections.singletonMap("bamboo.job", jobKey));

        });
        if (!bambooAgentLinks.isEmpty()) {
            bambooAgent.put("links", bambooAgentLinks);
        }
        if (!bambooAgentEnvVars.isEmpty()) {
            bambooAgent.put("environment", bambooAgentEnvVars);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(4);
        options.setCanonical(false);
        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }

    public static boolean isDockerInDockerImage(String image) {
        return image.contains("docker:") && image.endsWith("dind");
    }

}
