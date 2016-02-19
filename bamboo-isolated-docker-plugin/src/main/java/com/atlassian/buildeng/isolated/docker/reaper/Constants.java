package com.atlassian.buildeng.isolated.docker.reaper;

public interface Constants {
    public static final long   REAPER_THRESHOLD_MILLIS = 300000L; //Reap agents if they're older than 5 minutes
    public static final long   REAPER_INTERVAL_MILLIS  =  60000L; //Reap once a minute
    public static final String REAPER_KEY = "isolated-docker-reaper";
    public static final String REAPER_AGENT_MANAGER_KEY = "reaper-agent-manager";
    public static final String REAPER_AGENTS_HELPER_KEY = "reaper-agents-helper";
    public static final String REAPER_COMMAND_SENDER_KEY = "reaper-command-sender";
}
