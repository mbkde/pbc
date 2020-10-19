package com.atlassian.buildeng.kubernetes.serialization;

@FunctionalInterface
public interface ResponseMapper<T> {
    T map(byte[] data) throws DeserializationException;
}
