package com.atlassian.buildeng.kubernetes.shell;

import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;

import java.util.List;

public interface ShellExecutor {
    <T> T exec(List<String> args, ResponseMapper<T> responseMapper);
}
