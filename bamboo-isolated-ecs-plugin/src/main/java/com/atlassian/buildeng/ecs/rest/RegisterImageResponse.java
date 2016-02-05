package com.atlassian.buildeng.ecs.rest;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by obrent on 4/02/2016.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RegisterImageResponse {
    public Integer revision;
    public String failureReason;

    public RegisterImageResponse(Integer revision) {
        this.revision = revision;
    }

    public RegisterImageResponse(String failureReason) {
        this.failureReason = failureReason;
    }
}
