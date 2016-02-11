package com.atlassian.buildeng.spi.isolated.docker;

/**
 * Created by obrent on 8/02/2016.
 */
public class IsolatedDockerAgentException extends Exception {
    
    public IsolatedDockerAgentException(Throwable cause) {
        super(cause);
    }

    public IsolatedDockerAgentException(String message) {
        super(message);
    }

    public IsolatedDockerAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
