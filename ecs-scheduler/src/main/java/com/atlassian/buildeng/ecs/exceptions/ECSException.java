package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response.Status;

public class ECSException extends RestableIsolatedDockerException {

    public ECSException(Exception ecsException) {
        super(Status.INTERNAL_SERVER_ERROR, ecsException);
    }

    public ECSException(String message) {
        super(Status.INTERNAL_SERVER_ERROR, message);
    }
}
