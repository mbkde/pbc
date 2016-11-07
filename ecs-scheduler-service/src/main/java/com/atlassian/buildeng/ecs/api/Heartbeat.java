package com.atlassian.buildeng.ecs.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Heartbeat {
    private final String status = "ok";

    @JsonProperty
    public String getStatus() {
        return status;
    }
}
