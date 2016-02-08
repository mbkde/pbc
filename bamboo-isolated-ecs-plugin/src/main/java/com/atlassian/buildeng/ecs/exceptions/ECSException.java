package com.atlassian.buildeng.ecs.exceptions;

import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;

/**
 * Created by obrent on 8/02/2016.
 */
public class ECSException extends IsolatedDockerAgentException {
    private Exception ecsException;

    public ECSException(Exception ecsException) {
        this.ecsException = ecsException;
    }

    @Override
    public String toString() {
        return ecsException.toString();
    }
}
