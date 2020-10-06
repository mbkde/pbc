package com.atlassian.buildeng.kubernetes.serialization;

import io.fabric8.kubernetes.api.model.BaseKubernetesList;
import org.junit.Test;

import java.io.ByteArrayInputStream;

public class JsonResponseMapperTest {

    private JsonResponseMapper mapper = new JsonResponseMapper();

    @Test
    public void testSuccess() {
        Object map = mapper.map(getClass().getResourceAsStream("/fixture/kubectl/get-pods-1.json"));
        assert map instanceof BaseKubernetesList;
    }

    @Test(expected = DeserializationException.class)
    public void testFailure() {
        Object map = mapper.map(new ByteArrayInputStream("something-something".getBytes()));
    }
}