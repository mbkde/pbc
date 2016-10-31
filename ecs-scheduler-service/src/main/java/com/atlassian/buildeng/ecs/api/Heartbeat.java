package com.atlassian.buildeng.ecs.api;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Created by ojongerius on 09/05/2016.
 */
public class Heartbeat {
    private final String status = "ok";

    public  Heartbeat() {
        // For Jackson
    }

    public String Heartbeat() {
        // System.out.println("Running heartbeat");
        return this.status;
    }

    @JsonProperty
    public String getStatus() { return status; }
}
