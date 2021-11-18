package com.atlassian.buildeng.kubernetes.serialization;

import com.google.common.base.Charsets;
import io.fabric8.kubernetes.api.KubernetesHelper;
import java.io.IOException;

public class JsonResponseMapper implements ResponseMapper<Object> {
    @Override
    public Object map(byte[] data) throws DeserializationException {
        try {
            return KubernetesHelper.loadJson(data);
        } catch (IOException e) {
            throw new DeserializationException(new String(data, Charsets.UTF_8), e);
        }
    }
}
