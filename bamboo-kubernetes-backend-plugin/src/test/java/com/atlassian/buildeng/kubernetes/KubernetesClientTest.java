package com.atlassian.buildeng.kubernetes;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.atlassian.buildeng.kubernetes.exception.ConcurrentResourceQuotaModificationException;
import com.atlassian.buildeng.kubernetes.exception.ConnectionTimeoutException;
import com.atlassian.buildeng.kubernetes.exception.PodLimitQuotaExceededException;
import com.atlassian.buildeng.kubernetes.shell.ResponseStub;
import com.atlassian.buildeng.kubernetes.shell.StubShellExecutor;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

    @Test
    public void testCreatePodExceedsQuota() {
        assertThrows(PodLimitQuotaExceededException.class, () -> {
            StubShellExecutor shellExecutor = new StubShellExecutor();
            ResponseStub stub =  new ResponseStub("/fixture/kubectl/pod-quota-limit.txt", "/fixture/kubectl/empty.txt", 1);
            shellExecutor.addStub("kubectl --request-timeout=5m -o json create --validate=false -f /tmp/file.yaml", stub);
            KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
            client.createPod(new File("/tmp/file.yaml"));
        });
    }

    @Test
    public void testConcurrentResourceQuotaModificationException() {
        assertThrows(ConcurrentResourceQuotaModificationException.class, () -> {
            StubShellExecutor shellExecutor = new StubShellExecutor();
            ResponseStub stub =  new ResponseStub("/fixture/kubectl/resource-quota-concurrent.txt", "/fixture/kubectl/empty.txt", 1);
            shellExecutor.addStub("kubectl --request-timeout=5m -o json create --validate=false -f /tmp/file.yaml", stub);
            KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
            client.createPod(new File("/tmp/file.yaml"));
        });
    }

    @Test
    public void testConnectionTimeoutException() {
        assertThrows(ConnectionTimeoutException.class, () -> {
            StubShellExecutor shellExecutor = new StubShellExecutor();
            ResponseStub stub =  new ResponseStub("/fixture/kubectl/tls-connection-timeout.txt", "/fixture/kubectl/empty.txt", 1);
            shellExecutor.addStub("kubectl --request-timeout=5m -o json create --validate=false -f /tmp/file.yaml", stub);
            KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
            client.createPod(new File("/tmp/file.yaml"));
        });
    }


}
