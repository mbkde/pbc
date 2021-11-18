package com.atlassian.buildeng.kubernetes.serialization;

import com.atlassian.buildeng.kubernetes.shell.JavaShellExecutor;
import io.fabric8.kubernetes.api.model.BaseKubernetesList;
import java.io.IOException;
import org.junit.Test;

public class JsonResponseMapperTest {

    private JsonResponseMapper mapper = new JsonResponseMapper();

    @Test
    public void testSuccess() throws IOException {
        byte[] bytes = JavaShellExecutor.readBytes(getClass().getResourceAsStream("/fixture/kubectl/get-pods-1.json"));
        Object map = mapper.map(bytes);
        assert map instanceof BaseKubernetesList;
    }

    @Test(expected = DeserializationException.class)
    public void testFailure() {
        Object map = mapper.map("something-something".getBytes());
    }
}