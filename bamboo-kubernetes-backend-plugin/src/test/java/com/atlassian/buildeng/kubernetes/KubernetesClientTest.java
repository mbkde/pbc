package com.atlassian.buildeng.kubernetes;

import com.atlassian.buildeng.kubernetes.shell.StubShellExecutor;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesClientTest {

    @Mock
    GlobalConfiguration globalConfiguration;

    @Test
    public void testGetPods() {
        StubShellExecutor shellExecutor = new StubShellExecutor();
        shellExecutor.addStub("kubectl --request-timeout=5m -o json get pods --selector label=label_value", "/fixture/kubectl/get-pods-1.json");
        KubernetesClient client = new KubernetesClient(globalConfiguration, shellExecutor);
        List<Pod> list = client.getPodsByLabel("label", "label_value");
        assert list.size() == 1;
        assert list.get(0).getMetadata().getName().equals("tiller-deploy-8596f464bc-x5fh9");
    }
}
