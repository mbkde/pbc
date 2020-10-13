package com.atlassian.buildeng.kubernetes.exception;

public class ConnectionTimeoutException extends RecoverableKubectlException {
    public ConnectionTimeoutException(String message) {
        super(message);
    }

    public ConnectionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
