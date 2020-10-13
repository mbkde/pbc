package com.atlassian.buildeng.kubernetes.exception;

public class PodAlreadyExistsException extends RecoverableKubectlException {
    public PodAlreadyExistsException(String message) {
        super(message);
    }
}
