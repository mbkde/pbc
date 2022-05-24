package com.atlassian.buildeng.kubernetes.shell;

import com.atlassian.buildeng.kubernetes.serialization.DeserializationException;
import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;
import com.google.common.base.Charsets;
//import io.fabric8.utils.Files;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;

public class StubShellExecutor implements ShellExecutor {
    private Map<String, ResponseStub> responses = new HashMap<>();

    public StubShellExecutor() {
    }

    @Override
    public <T> T exec(List<String> args, ResponseMapper<T> responseMapper) {
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            builder.append(arg);
            builder.append(' ');
        }

        builder.deleteCharAt(builder.length() - 1);
        ResponseStub response = responses.get(builder.toString());
        if (response == null) {
            throw new RuntimeException("Did you forget to setup a shell stubPath for '" + builder.toString() + "' ?");
        }

        if (response.getReturnCode() != 0) {
            String stdout = null;
            String stderr = null;
            try {
                stdout = IOUtils.toString(getClass().getResourceAsStream(response.getStdout()), Charsets.UTF_8);
                stderr = IOUtils.toString(getClass().getResourceAsStream(response.getStderr()), Charsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Invalid configuration of stubs");
            }
            throw new ShellException("Non-zero exit code",
                    stdout,
                    stderr,
                    response.getReturnCode(),
                    args);
        }

        try {
//            byte[] bytes = Files.readBytes(getClass().getResourceAsStream(response.getStdout()));
            byte[] bytes = IOUtils.toByteArray(getClass().getResourceAsStream(response.getStdout()));
            return responseMapper.map(bytes);
        } catch (DeserializationException | IOException e) {
            throw new ShellException("Unable to parse kubectl response", e.getMessage(), "", 0, args);
        }
    }

    public void addStub(String execString, ResponseStub stubPath) {
        responses.put(execString, stubPath);
    }
}