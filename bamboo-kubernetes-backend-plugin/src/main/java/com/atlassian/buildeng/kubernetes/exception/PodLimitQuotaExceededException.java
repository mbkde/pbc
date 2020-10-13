package com.atlassian.buildeng.kubernetes.exception;

public class PodLimitQuotaExceededException extends RecoverableKubectlException {
    public PodLimitQuotaExceededException(String message) {
        super(message);
    }

    public PodLimitQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
