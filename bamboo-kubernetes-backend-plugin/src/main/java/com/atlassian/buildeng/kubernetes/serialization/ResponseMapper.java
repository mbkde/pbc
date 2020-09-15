package com.atlassian.buildeng.kubernetes.serialization;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface ResponseMapper<T> {
    T map(InputStream inputStream) throws IOException;
}
