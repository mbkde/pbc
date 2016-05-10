package com.atlassian.buildeng.spi.isolated.docker;


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
