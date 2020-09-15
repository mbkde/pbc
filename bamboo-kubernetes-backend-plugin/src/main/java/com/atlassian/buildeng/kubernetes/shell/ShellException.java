package com.atlassian.buildeng.kubernetes.shell;

public class ShellException extends RuntimeException {
    public ShellException(String message, Exception exception) {
        super(message, exception);
    }

    public ShellException(String message) {
        super(message);
    }
}
