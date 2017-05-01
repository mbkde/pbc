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
package com.atlassian.buildeng.ecs.metrics;

import com.atlassian.bamboo.artifact.Artifact;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.CustomBuildProcessor;
import com.atlassian.bamboo.build.artifact.ArtifactHandlerPublishingResult;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContextImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactPublishingResult;
import com.atlassian.bamboo.security.SecureToken;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildContextHelper;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.buildeng.spi.isolated.docker.AccessConfiguration;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.base.Joiner;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.rrd4j.ConsolFun;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdSafeFileBackendFactory;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After the build extracts the rrd files from a source directory and generates the
 * images and uploads them as artifacts.
 */
public class MetricsBuildProcessor  implements CustomBuildProcessor {
    private static final Logger logger = LoggerFactory.getLogger(MetricsBuildProcessor.class);
    Color COLOR_GREEN = Color.decode("0x29C30B");
    Color COLOR_RED = Color.decode("0xF71C31");
    Color COLOR_YELLOW = Color.decode("0xDBDE00");
    Color COLOR_ORANGE = Color.decode("0xF7B71C");
    Color COLOR_BLUE = Color.decode("0x0B80C3");

    private final BuildLoggerManager buildLoggerManager;
    private BuildContext buildContext;
    private final ArtifactManager artifactManager;


    private MetricsBuildProcessor(BuildLoggerManager buildLoggerManager, ArtifactManager artifactManager) {
        this.buildLoggerManager = buildLoggerManager;
        this.artifactManager = artifactManager;
    }

    @Override
    public void init(@NotNull BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @NotNull
    @Override
    public BuildContext call() {
        Configuration config = AccessConfiguration.forContext(buildContext);
    
        if (config.isEnabled()) {
            BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getResultKey());
            generateMetricsGraphs(buildLogger);
        }

        return buildContext;
    }

    private void generateMetricsGraphs(BuildLogger buildLogger) {
        String token = buildContext.getCurrentResult().getCustomBuildData().remove("secureToken");
        String taskArn = buildContext.getCurrentResult().getCustomBuildData().get("result.isolated.docker.TaskARN");
        if (taskArn != null && token != null) {
            //arn:aws:ecs:us-east-1:960714566901:task/c15f4e79-6eb9-4051-9951-018916920a9a
            String taskId = taskArn.substring(taskArn.indexOf("/"));
            File rootFolder = new File(MetricHostFolderMapping.CONTAINER_METRICS_PATH  + "/tasks");
            File taskFolder = new File(rootFolder, taskId);
            if (taskFolder.isDirectory()) {

                final Map<String, String> artifactHandlerConfiguration = BuildContextHelper.getArtifactHandlerConfiguration(buildContext);
                File buildWorkingDirectory = BuildContextHelper.getBuildWorkingDirectory((CommonContext)buildContext);
                final SecureToken secureToken = SecureToken.createFromString(token);
                
                List<String> names = new ArrayList<>();
                for (File containerFolder : taskFolder.listFiles((File pathname) -> pathname.isDirectory() && !"~internal~ecs-emptyvolume-source".equals(pathname.getName()) &&  !"bamboo-agent-sidekick".equals(pathname.getName()))) {
                    long startTime;
                    long endTime;
                    try {
                        new File(containerFolder, "stop").createNewFile();
                        Thread.sleep(100); //sleep a bit to make sure the provider is no longer writing there.
                        startTime = Long.parseLong(FileUtils.readFileToString(new File(containerFolder, "start.txt")).trim());
                        endTime = Long.parseLong(FileUtils.readFileToString(new File(containerFolder, "end.txt")).trim());
                        //rrd4j has it's own format, we need to convert from rrd first
                        RrdDb rrd = new RrdDb(new File(containerFolder, "cpu.usage.rrd4j").getAbsolutePath(), "rrdtool:/" + new File(containerFolder, "cpu.usage.rrd").getAbsolutePath(), new RrdSafeFileBackendFactory());
                        rrd.close();
                        RrdDb rrd2 = new RrdDb(new File(containerFolder, "memory.usage.rrd4j").getAbsolutePath(), "rrdtool:/" + new File(containerFolder, "memory.usage.rrd").getAbsolutePath(), new RrdSafeFileBackendFactory());
                        rrd2.close();

                    } catch (IOException | InterruptedException | NumberFormatException ex) {
                        startTime = 0;
                        endTime = 0;
                        buildLogger.addErrorLogEntry("Error while processing rrd files", ex);
                    }
                    File targetDir = new File(buildWorkingDirectory, ".pbc-metrics");
                    targetDir.mkdirs();
                    String cpuName = containerFolder.getName() + "-cpu";
                    String memoryName = containerFolder.getName() + "-memory";
                    generateCpuPng(createGraphDef(startTime, endTime, containerFolder.getName() + " CPU Usage", "CPU Cores", targetDir, cpuName + ".png"), containerFolder, buildLogger);
                    generateMemoryPng(createGraphDef(startTime, endTime, containerFolder.getName() + " Memory Usage", "Memory Usage", targetDir, memoryName + ".png"), containerFolder, buildLogger);
                    publishImage(cpuName, secureToken, buildLogger, buildWorkingDirectory, artifactHandlerConfiguration, buildContext);
                    publishImage(memoryName, secureToken, buildLogger, buildWorkingDirectory, artifactHandlerConfiguration, buildContext);
                    names.add("pbc-metrics-" + cpuName);
                    names.add("pbc-metrics-"+ memoryName);
                }
                buildContext.getCurrentResult().getCustomBuildData().put("image_artifacts", Joiner.on(",").join(names));
            } else {
                buildLogger.addBuildLogEntry("Folder with metrics data not mounted");
            }
        }
    }

    private void publishImage(String name, SecureToken secureToken, BuildLogger buildLogger, File buildWorkingDirectory, final Map<String, String> artifactHandlerConfiguration, BuildContext buildContext) {
        ArtifactDefinitionContextImpl artifact = new ArtifactDefinitionContextImpl("pbc-metrics-" + name, false, secureToken);
        artifact.setCopyPattern(name + ".png");
        artifact.setLocation(".pbc-metrics");
        final ArtifactPublishingResult publishingResult =
                artifactManager.publish(buildLogger,
                        buildContext.getPlanResultKey(),
                        buildWorkingDirectory,
                        artifact,
                        artifactHandlerConfiguration,
                        0);
        buildContext.getCurrentResult().getCustomBuildData().put("image_artifacts_type", publishingResult.getSuccessfulPublishingResults().stream().findAny().map((ArtifactHandlerPublishingResult t) -> t.getArtifactHandlerKey()).orElse(Artifact.SYSTEM_LINK_TYPE));
        buildLogger.addBuildLogEntry("Generated and published '" + name + "' container performance image.");
    }

    private void generateCpuPng(RrdGraphDef gDef, File containerFolder, BuildLogger buildLogger) {
        gDef.datasource("user", new File(containerFolder, "cpu.usage.rrd4j").getAbsolutePath(), "user", ConsolFun.AVERAGE);
        gDef.datasource("system", new File(containerFolder, "cpu.usage.rrd4j").getAbsolutePath(), "system", ConsolFun.AVERAGE);
        gDef.datasource("throttled", new File(containerFolder, "cpu.usage.rrd4j").getAbsolutePath(), "throttled", ConsolFun.AVERAGE);
        gDef.datasource("user_sec", "user,100,/"); //10-millisecond --> seconds
        gDef.datasource("system_sec", "system,100,/");
        gDef.datasource("throttled_sec", "throttled,100,/");
        gDef.datasource("user_min", "user_sec", ConsolFun.MIN);
        gDef.datasource("user_avg", "user_sec", ConsolFun.AVERAGE);
        gDef.datasource("user_max", "user_sec", ConsolFun.MAX);
        gDef.datasource("system_min", "system_sec", ConsolFun.MIN);
        gDef.datasource("system_avg", "system_sec", ConsolFun.AVERAGE);
        gDef.datasource("system_max", "system_sec", ConsolFun.MAX);
        gDef.datasource("throttled_min", "throttled_sec", ConsolFun.MIN);
        gDef.datasource("throttled_avg", "throttled_sec", ConsolFun.AVERAGE);
        gDef.datasource("throttled_max", "throttled_sec", ConsolFun.MAX);

        gDef.area("user_sec", COLOR_GREEN, "CPU user", false);
        gDef.gprint("user_min", "Min: %10.2lf%Ss");
        gDef.gprint("user_avg", "Avg: %10.2lf%Ss");
        gDef.gprint("user_max", "Max: %10.2lf%Ss\\l");
        gDef.area("system_sec", COLOR_BLUE, "CPU system", true);
        gDef.gprint("system_min", "Min: %10.2lf%Ss");
        gDef.gprint("system_avg", "Avg: %10.2lf%Ss");
        gDef.gprint("system_max", "Max: %10.2lf%Ss\\l");
        gDef.area("throttled_sec", COLOR_RED, "CPU throttled", true);
        gDef.gprint("throttled_min", "Min: %10.2lf%Ss");
        gDef.gprint("throttled_avg", "Avg: %10.2lf%Ss");
        gDef.gprint("throttled_max", "Max: %10.2lf%Ss\\l");
        try {
            RrdGraph graph = new RrdGraph(gDef);
        } catch (IOException e) {
            buildLogger.addErrorLogEntry("Error while generating rrd graph", e);
        }
    }

    private RrdGraphDef createGraphDef(long startTime, long endTime, String title, String vertLabel, File targetFolder, String targetName) {
        RrdGraphDef gDef = new RrdGraphDef();
        gDef.setImageFormat("png");
        gDef.setWidth(800);
        gDef.setHeight(200);
        gDef.setAltAutoscaleMax(true);
        gDef.setTitle(title);
        gDef.setVerticalLabel(vertLabel);
        gDef.setStartTime(startTime);
        gDef.setEndTime(endTime);
        gDef.setFilename(new File(targetFolder, targetName).getAbsolutePath());
        return gDef;
    }

    private void generateMemoryPng(RrdGraphDef gDef, File containerFolder, BuildLogger buildLogger) {
        gDef.datasource("cache", new File(containerFolder, "memory.usage.rrd4j").getAbsolutePath(), "cache", ConsolFun.AVERAGE);
        gDef.datasource("rss", new File(containerFolder, "memory.usage.rrd4j").getAbsolutePath(), "rss", ConsolFun.AVERAGE);
        gDef.datasource("swap", new File(containerFolder, "memory.usage.rrd4j").getAbsolutePath(), "swap", ConsolFun.AVERAGE);
        gDef.datasource("total", new File(containerFolder, "memory.usage.rrd4j").getAbsolutePath(), "total", ConsolFun.AVERAGE);
        gDef.datasource("limit", new File(containerFolder, "memory.usage.rrd4j").getAbsolutePath(), "limit", ConsolFun.AVERAGE);

        gDef.datasource("cache_min", "cache", ConsolFun.MIN);
        gDef.datasource("cache_avg", "cache", ConsolFun.AVERAGE);
        gDef.datasource("cache_max", "cache", ConsolFun.MAX);
        gDef.datasource("rss_min", "rss", ConsolFun.MIN);
        gDef.datasource("rss_avg", "rss", ConsolFun.AVERAGE);
        gDef.datasource("rss_max", "rss", ConsolFun.MAX);
        gDef.datasource("swap_min", "swap", ConsolFun.MIN);
        gDef.datasource("swap_avg", "swap", ConsolFun.AVERAGE);
        gDef.datasource("swap_max", "swap", ConsolFun.MAX);
        gDef.datasource("total_min", "total", ConsolFun.MIN);
        gDef.datasource("total_avg", "total", ConsolFun.AVERAGE);
        gDef.datasource("total_max", "total", ConsolFun.MAX);
        gDef.datasource("limit_bla", "total", ConsolFun.MAX);

        gDef.area("cache", COLOR_BLUE, "Cache", false);
        gDef.gprint("cache_min", "Min: %10.2lf%SB");
        gDef.gprint("cache_avg", "Avg: %10.2lf%SB");
        gDef.gprint("cache_max", "Max: %10.2lf%SB\\l");
        gDef.area("rss", COLOR_GREEN, "Rss", true);
        gDef.gprint("rss_min", "Min: %10.2lf%SB");
        gDef.gprint("rss_avg", "Avg: %10.2lf%SB");
        gDef.gprint("rss_max", "Max: %10.2lf%SB\\l");
        gDef.area("swap", COLOR_ORANGE, "Swap", true);
        gDef.gprint("swap_min", "Min: %10.2lf%SB");
        gDef.gprint("swap_avg", "Avg: %10.2lf%SB");
        gDef.gprint("swap_max", "Max: %10.2lf%SB\\l");
        gDef.comment("Total     ");
        gDef.gprint("total_min", "Min: %10.2lf%SB");
        gDef.gprint("total_avg", "Avg: %10.2lf%SB");
        gDef.gprint("total_max", "Max: %10.2lf%SB\\l");
        gDef.comment("\n");
        gDef.line("limit", COLOR_RED);
        gDef.gprint("limit_bla", "%10.2lf%SB\\l");

        try {
            RrdGraph graph = new RrdGraph(gDef);
        } catch (IOException e) {
            buildLogger.addErrorLogEntry("Error while generating rrd graph", e);
        }

    }

}
