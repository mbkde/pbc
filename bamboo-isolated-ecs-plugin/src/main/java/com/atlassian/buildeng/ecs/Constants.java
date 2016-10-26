package com.atlassian.buildeng.ecs;

public interface Constants extends com.atlassian.buildeng.ecs.scheduling.Constants {

    // The name used for the generated task definition (a.k.a. family)
    String TASK_DEFINITION_SUFFIX = "-generated";

    // The name of the atlassian docker registry sidekick
    String DEFAULT_SIDEKICK_REPOSITORY = "docker.atlassian.io/buildeng/bamboo-agent-sidekick";

    // The default cluster to use
    String DEFAULT_CLUSTER = "default";

    long   PLUGIN_JOB_INTERVAL_MILLIS  =  60000L; //Reap once every 60 seconds
    String PLUGIN_JOB_KEY = "ecs-watchdog";
    
    //these 2 copied from bamboo-isolated-docker-plugin to avoid dependency
    String RESULT_PREFIX = "result.isolated.docker.";
    String RESULT_ERROR = "custom.isolated.docker.error";

}