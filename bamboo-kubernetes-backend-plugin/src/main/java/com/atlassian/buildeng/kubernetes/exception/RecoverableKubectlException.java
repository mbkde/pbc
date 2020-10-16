package com.atlassian.buildeng.kubernetes.exception;

public abstract class RecoverableKubectlException extends KubectlException {
    public RecoverableKubectlException(String message) {
        super(message);
    }

    public RecoverableKubectlException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isRecoverable() {
        return true;
    }
}
