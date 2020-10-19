package com.atlassian.buildeng.kubernetes.serialization;

import java.io.IOException;
import org.apache.commons.io.IOUtils;

public class StringResponseMapper implements ResponseMapper<String> {
    @Override
    public String map(byte[] data) throws DeserializationException {
        try {
            return IOUtils.toString(data, "UTF-8");
        } catch (IOException e) {
            throw new DeserializationException(e);
        }
    }
}
