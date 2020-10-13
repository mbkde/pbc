package com.atlassian.buildeng.kubernetes.exception;

public class ClusterRegistryKubectlException extends KubectlException {

    public ClusterRegistryKubectlException(String message) {
        super(message);
    }

    public ClusterRegistryKubectlException(String message, Throwable cause) {
        super(message, cause);
    }
}
