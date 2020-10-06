package com.atlassian.buildeng.kubernetes.serialization;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

public class StringResponseMapper implements ResponseMapper<String> {
    @Override
    public String map(InputStream inputStream) throws DeserializationException {
        try {
            return IOUtils.toString(inputStream, Charsets.UTF_8);
        } catch (IOException e) {
            throw new DeserializationException(e);
        }
    }
}
