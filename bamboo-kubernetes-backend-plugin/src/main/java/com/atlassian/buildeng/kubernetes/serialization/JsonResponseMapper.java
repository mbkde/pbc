package com.atlassian.buildeng.kubernetes.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import java.io.IOException;

public class JsonResponseMapper implements ResponseMapper<Object> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Object map(byte[] data) throws DeserializationException {
        try {
            if (data != null && data.length > 0) {
                return OBJECT_MAPPER.readerFor(KubernetesResource.class).readValue(data);
            }
            return null;
        } catch (IOException e) {
            throw new DeserializationException(new String(data, Charsets.UTF_8), e);
        }
    }
}
