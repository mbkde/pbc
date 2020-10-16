package com.atlassian.buildeng.kubernetes.exception;

import com.atlassian.buildeng.kubernetes.shell.ShellException;

public class KubernetesExceptionParser {
    /**
     * Parses the process output to determine an appropriate exception.
     * @param errorMessage error message text
     * @param exception shell exception that needs parsing
     * @return appropriate KubectlException for the particular error message
     */
    public KubectlException map(String errorMessage, ShellException exception) {
        String stdout = exception.getStdout();

        if (stdout == null) {
            return new KubectlException(errorMessage, exception);
        }

        if (stdout.contains("Operation cannot be fulfilled on resourcequotas \"pod-limit\": the object has been modified")) {
            //see https://github.com/kubernetes/kubernetes/issues/67761
            throw new ConcurrentResourceQuotaModificationException("Too many parallel requests in-flight", exception);
        } else if (stdout.contains("is forbidden: exceeded quota: pod-limit")) {
            throw new PodLimitQuotaExceededException("pod-limit reached", exception);
        } else if (stdout.contains("net/http: TLS handshake timeout")) {
            throw new ConnectionTimeoutException("Unable to connect to Kubernetes API", exception);
        } else if (stdout.contains("(AlreadyExists)") && stdout.contains("error when creating")) {
            throw new PodAlreadyExistsException("pod already exists");
        }

        return new KubectlException(errorMessage, exception);
    }
}
