package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response.Status;

public class RevisionNotActiveException extends RestableIsolatedDockerException {

    public RevisionNotActiveException(Integer revision) {
        super(Status.BAD_REQUEST, String.format("Revision %d is not active", revision));
    }

}
