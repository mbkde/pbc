package com.atlassian.buildeng.kubernetes.serialization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DeserializationException extends RuntimeException {

    public DeserializationException(@Nonnull String message) {
        super(message);
    }

    public DeserializationException(@Nullable Throwable cause) {
        super(cause);
    }

    public DeserializationException(@Nonnull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
