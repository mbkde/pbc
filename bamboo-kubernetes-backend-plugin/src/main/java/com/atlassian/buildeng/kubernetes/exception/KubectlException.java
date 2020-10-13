package com.atlassian.buildeng.kubernetes.exception;

public class KubectlException extends RuntimeException {
    public KubectlException(String message) {
        super(message);
    }

    public KubectlException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean isRecoverable() {
        return false;
    }
}
