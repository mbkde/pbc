package com.atlassian.buildeng.kubernetes.serialization;

import io.fabric8.kubernetes.api.model.BaseKubernetesList;
import io.fabric8.utils.Files;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class JsonResponseMapperTest {

    private JsonResponseMapper mapper = new JsonResponseMapper();

    @Test
    public void testSuccess() throws IOException {
        byte[] bytes = Files.readBytes(getClass().getResourceAsStream("/fixture/kubectl/get-pods-1.json"));
        Object map = mapper.map(bytes);
        assert map instanceof BaseKubernetesList;
    }

    @Test
    public void testFailure() {
        assertThrows(DeserializationException.class, () -> {
            Object map = mapper.map("something-something".getBytes());
        });
    }
}