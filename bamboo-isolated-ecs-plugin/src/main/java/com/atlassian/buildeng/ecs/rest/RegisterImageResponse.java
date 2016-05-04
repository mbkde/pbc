package com.atlassian.buildeng.ecs.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;


@JsonInclude(Include.NON_EMPTY)
public class RegisterImageResponse {
    private Integer revision;
    private String failureReason;

    public RegisterImageResponse(Integer revision) {
        this.revision = revision;
    }

    public RegisterImageResponse(String failureReason) {
        this.failureReason = failureReason;
    }
}
