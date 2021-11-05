package com.atlassian.buildeng.kubernetes.shell;

import com.atlassian.buildeng.kubernetes.serialization.DeserializationException;
import com.atlassian.buildeng.kubernetes.serialization.ResponseMapper;
import com.google.common.base.Charsets;
import static io.fabric8.kubernetes.client.utils.Utils.closeQuietly;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JavaShellExecutor implements ShellExecutor {
    private static final Logger logger = LoggerFactory.getLogger(JavaShellExecutor.class);

    @Override
    public <T> T exec(List<String> args, ResponseMapper<T> responseMapper) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            // kubectl requires HOME env to find the config, but the Bamboo server JVM might not have it setup.
            pb.environment().put("HOME", System.getProperty("user.home"));
            Process process = pb.start();

            logger.debug("starting process");
            byte[] data = readBytes(process.getInputStream());

            int ret = process.waitFor();
            logger.debug("process finished");

            if (ret != 0) {
                throw new ShellException(
                        "Non-zero exit code",
                        IOUtils.toString(data, "UTF-8"),
                        IOUtils.toString(process.getErrorStream(), Charsets.UTF_8),
                        ret
                );
            }

            logger.debug("mapping response for exit code {}", ret);
            T output = responseMapper.map(data);
            logger.debug("mapping response finished");
            return output;
        } catch (DeserializationException x) {
            throw new ShellException("Unable to parse kubectl response", x.getMessage(), "", 0);
        } catch (IOException | InterruptedException x) {
            throw new ShellException("" + x.getMessage(), x);
        } catch (Exception e) {
            logger.error("Exception while executing shell", e);
            throw e;
        }
    }

    public static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = null;
        if (in == null) {
            throw new FileNotFoundException("No InputStream specified");
        } else {
            try {
                bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];

                int remaining;
                while((remaining = in.read(buffer)) > 0) {
                    bos.write(buffer, 0, remaining);
                }

                return bos.toByteArray();
            } finally {
                closeQuietly(in);
                closeQuietly(bos);
            }
        }
    }
}
