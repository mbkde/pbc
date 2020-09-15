package com.atlassian.buildeng.kubernetes.shell;

import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.List;

public class JavaShellExecutor implements ShellExecutor {
    @Override
    public <T> T exec(List<String> args, ResponseMapper<T> responseMapper) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            // kubectl requires HOME env to find the config, but the Bamboo server JVM might not have it setup.
            pb.environment().put("HOME", System.getProperty("user.home"));
            Process process = pb.start();
            T output = responseMapper.map(process.getInputStream());
            int ret = process.waitFor();
            if (ret != 0) {
                throw new ShellException(IOUtils.toString(process.getErrorStream(), Charsets.UTF_8));
            }

            return output;
        } catch (IOException | InterruptedException x) {
            throw new ShellException("" + x.getMessage(), x);
        }
    }
}
