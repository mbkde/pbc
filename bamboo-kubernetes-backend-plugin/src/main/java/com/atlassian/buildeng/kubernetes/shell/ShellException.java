package com.atlassian.buildeng.kubernetes.shell;

import javax.annotation.Nullable;

public class ShellException extends RuntimeException {
    @Nullable private String stdout;
    @Nullable private String stderr;
    @Nullable private int returnCode;

    public ShellException(String message,
                          String stdout,
                          String stderr,
                          int returnCode) {
        super(message);
        this.stdout = stdout;
        this.stderr = stderr;
        this.returnCode = returnCode;
    }

    public ShellException(String message, Throwable cause) {
        super(message, cause);
    }

    public int getReturnCode() {
        return returnCode;
    }

    public String getStderr() {
        return stderr;
    }

    public String getStdout() {
        return stdout;
    }
}
