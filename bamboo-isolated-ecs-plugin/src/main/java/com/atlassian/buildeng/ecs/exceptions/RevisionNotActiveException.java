package com.atlassian.buildeng.ecs.exceptions;

import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;

/**
 * Created by obrent on 8/02/2016.
 */
public class RevisionNotActiveException extends IsolatedDockerAgentException {
    private Integer revision;

    public RevisionNotActiveException(Integer revision) {
        this.revision = revision;
    }

    @Override
    public String toString() {
        return String.format("Revision %d is not active", revision);
    }
}
