package com.atlassian.buildeng.kubernetes.exception;

import com.atlassian.buildeng.kubernetes.shell.ShellException;
import org.junit.Test;

public class KubernetesExceptionParserTest {

    private KubernetesExceptionParser mapper = new KubernetesExceptionParser();

    @Test(expected = PodLimitQuotaExceededException.class)
    public void testPodLimitQuotaException() {
        ShellException shellException = withErrorInStdout("Error from server (Forbidden): error when creating \"/opt/atlassian/bamboo/temp/pod4298680591083559475yaml\": pods \"atlasbi-atlasbidb113-jirajdk8linuxmysql-1-10f5e985-01d4-4451-b869-bb768984271e\" is forbidden: exceeded quota: pod-limit, requested: pods=1, used: pods=1500, limited: pods=1500");
        KubectlException kubectlException = mapper.map("kubectl returned non-zero exit code.", shellException);
    }

    @Test(expected = ConnectionTimeoutException.class)
    public void testTimeoutException() {
        ShellException shellException = withErrorInStdout("error: error when creating \\\"/opt/atlassian/bamboo/temp/pod1yaml\\\": Post https://kubernetes.xxxxx.kitt-inf.net/api/v1/namespaces/yyy/pods?timeout=5m0s: net/http: TLS handshake timeout");
        KubectlException kubectlException = mapper.map("kubectl returned non-zero exit code.", shellException);
    }

    @Test(expected = ConcurrentResourceQuotaModificationException.class)
    public void testConcurrentResourceQuotaModificationException() {
        ShellException shellException = withErrorInStdout("Error from server (Conflict): error when creating \\\"/opt/atlassian/bamboo/temp/pod1yaml\\\": Operation cannot be fulfilled on resourcequotas \"pod-limit\": the object has been modified; please apply your changes to the latest version and try again");
        KubectlException kubectlException = mapper.map("kubectl returned non-zero exit code.", shellException);
    }

    private ShellException withErrorInStdout(String message) {
        return new ShellException("some error", message, "", 1);
    }
}