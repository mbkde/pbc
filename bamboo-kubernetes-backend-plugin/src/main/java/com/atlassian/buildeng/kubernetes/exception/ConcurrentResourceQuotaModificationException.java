package com.atlassian.buildeng.kubernetes.exception;

public class ConcurrentResourceQuotaModificationException extends RecoverableKubectlException {
    public ConcurrentResourceQuotaModificationException(String message) {
        super(message);
    }

    public ConcurrentResourceQuotaModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
