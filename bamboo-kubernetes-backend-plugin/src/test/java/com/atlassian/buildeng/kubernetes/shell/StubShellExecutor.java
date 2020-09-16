package com.atlassian.buildeng.kubernetes.shell;

import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StubShellExecutor implements ShellExecutor {
    private Map<String, String> responses = new HashMap<>();

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
        String response = responses.get(builder.toString());
        if (response == null) {
            throw new RuntimeException("Did you forget to setup a shell stubPath for '" + builder.toString() + "' ?");
        }

        try {
            return responseMapper.map(getClass().getResourceAsStream(response));
        } catch (IOException e) {
            throw new RuntimeException("Did you forget to create a stub file at '" + response + "' ?");
        }
    }

    public void addStub(String execString, String stubPath) {
        responses.put(execString, stubPath);
    }
}