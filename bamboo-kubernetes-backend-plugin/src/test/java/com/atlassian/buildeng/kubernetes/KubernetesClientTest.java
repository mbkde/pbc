package com.atlassian.buildeng.kubernetes;

import com.atlassian.buildeng.kubernetes.exception.ConcurrentResourceQuotaModificationException;
import com.atlassian.buildeng.kubernetes.exception.ConnectionTimeoutException;
import com.atlassian.buildeng.kubernetes.exception.PodLimitQuotaExceededException;
import com.atlassian.buildeng.kubernetes.shell.ResponseStub;
import com.atlassian.buildeng.kubernetes.shell.StubShellExecutor;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesClientTest {

    @Mock
    GlobalConfiguration globalConfiguration;

    @Test
    public void testGetPods() {
        StubShellExecutor shellExecutor = new StubShellExecutor();
        ResponseStub stub =  new ResponseStub("/fixture/kubectl/get-pods-1.json", "/fixture/kubectl/empty.txt", 0);
        shellExecutor.addStub("kubectl --request-timeout=5m -o json get pods --selector label=label_value", stub);
        KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
        List<Pod> list = client.getPodsByLabel("label", "label_value");
        assert list.size() == 1;
        assert list.get(0).getMetadata().getName().equals("tiller-deploy-8596f464bc-x5fh9");
    }

    @Test(expected = PodLimitQuotaExceededException.class)
    public void testCreatePodExceedsQuota() {
        StubShellExecutor shellExecutor = new StubShellExecutor();
        ResponseStub stub =  new ResponseStub("/fixture/kubectl/pod-quota-limit.txt", "/fixture/kubectl/empty.txt", 1);
        shellExecutor.addStub("kubectl --request-timeout=5m -o json create --validate=false -f /tmp/file.yaml", stub);
        KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
        client.createPod(new File("/tmp/file.yaml"));
    }

    @Test(expected = ConcurrentResourceQuotaModificationException.class)
    public void testConcurrentResourceQuotaModificationException() {
        StubShellExecutor shellExecutor = new StubShellExecutor();
        ResponseStub stub =  new ResponseStub("/fixture/kubectl/resource-quota-concurrent.txt", "/fixture/kubectl/empty.txt", 1);
        shellExecutor.addStub("kubectl --request-timeout=5m -o json create --validate=false -f /tmp/file.yaml", stub);
        KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
        client.createPod(new File("/tmp/file.yaml"));
    }

    @Test(expected = ConnectionTimeoutException.class)
    public void testConnectionTimeoutException() {
        StubShellExecutor shellExecutor = new StubShellExecutor();
        ResponseStub stub =  new ResponseStub("/fixture/kubectl/tls-connection-timeout.txt", "/fixture/kubectl/empty.txt", 1);
        shellExecutor.addStub("kubectl --request-timeout=5m -o json create --validate=false -f /tmp/file.yaml", stub);
        KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
        client.createPod(new File("/tmp/file.yaml"));
    }


}
