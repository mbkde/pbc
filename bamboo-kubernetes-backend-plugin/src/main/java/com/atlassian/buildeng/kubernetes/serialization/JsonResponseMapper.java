package com.atlassian.buildeng.kubernetes.serialization;

import io.fabric8.kubernetes.api.KubernetesHelper;

import java.io.IOException;
import java.io.InputStream;

public class JsonResponseMapper implements ResponseMapper<Object> {
    @Override
    public Object map(InputStream inputStream) throws IOException {
        return KubernetesHelper.loadJson(inputStream);
    }
}
