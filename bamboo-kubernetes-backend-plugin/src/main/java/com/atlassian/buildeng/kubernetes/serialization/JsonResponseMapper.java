package com.atlassian.buildeng.kubernetes.serialization;

import com.google.common.base.Charsets;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.utils.Files;

import java.io.IOException;
import java.io.InputStream;

public class JsonResponseMapper implements ResponseMapper<Object> {
    @Override
    public Object map(InputStream inputStream) throws DeserializationException {
        byte[] data = null;
        try {
            data = Files.readBytes(inputStream);
        } catch (IOException e) {
            throw new DeserializationException(e);
        }

        try {
            return KubernetesHelper.loadJson(data);
        } catch (IOException e) {
            throw new DeserializationException(new String(data, Charsets.UTF_8), e);
        }
    }
}
