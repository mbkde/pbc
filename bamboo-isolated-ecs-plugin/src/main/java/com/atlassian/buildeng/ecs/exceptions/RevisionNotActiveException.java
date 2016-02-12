package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response;

/**
 * Created by obrent on 8/02/2016.
 */
public class RevisionNotActiveException extends RestableIsolatedDockerException {

    public RevisionNotActiveException(Integer revision) {
        super(Response.Status.BAD_REQUEST, String.format("Revision %d is not active", revision));
    }

}
