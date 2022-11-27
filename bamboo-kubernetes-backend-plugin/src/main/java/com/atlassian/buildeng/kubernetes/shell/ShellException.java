package com.atlassian.buildeng.kubernetes.shell;

import java.util.List;
import javax.annotation.Nullable;

public class ShellException extends RuntimeException {
    @Nullable
    private String stdout;
    @Nullable
    private String stderr;
    @Nullable
    private int returnCode;
    private List<String> arguments;

    public ShellException(String message, String stdout, String stderr, int returnCode, List<String> arguments) {
        super(message);
        this.stdout = stdout;
        this.stderr = stderr;
        this.returnCode = returnCode;
        this.arguments = arguments;
    }

    public ShellException(String message, Throwable cause, List<String> arguments) {
        super(message, cause);
        this.arguments = arguments;
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

    public String getArgumentsAsString() {
        return String.join(" ", arguments);
    }
}
