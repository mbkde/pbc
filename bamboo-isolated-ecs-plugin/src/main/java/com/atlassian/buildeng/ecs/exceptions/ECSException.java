package com.atlassian.buildeng.ecs.exceptions;

import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.ServerException;
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
