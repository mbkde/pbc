package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response;

/**
 * Created by obrent on 8/02/2016.
 */
public class ECSException extends RestableIsolatedDockerException {

    public ECSException(Exception ecsException) {
        super(Response.Status.INTERNAL_SERVER_ERROR, ecsException);
    }

}
