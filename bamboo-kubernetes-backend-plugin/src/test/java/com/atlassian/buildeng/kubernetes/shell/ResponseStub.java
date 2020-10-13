package com.atlassian.buildeng.kubernetes.shell;

public class ResponseStub {
    private String stdout;
    private String stderr;
    private int returnCode;

    public ResponseStub(String stdout, String stderr, int returnCode) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.returnCode = returnCode;
    }

    public String getStderr() {
        return stderr;
    }

    public String getStdout() {
        return stdout;
    }

    public int getReturnCode() {
        return returnCode;
    }
}
