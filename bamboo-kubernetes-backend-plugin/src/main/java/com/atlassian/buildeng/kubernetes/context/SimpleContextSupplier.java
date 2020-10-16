package com.atlassian.buildeng.kubernetes.context;

import javax.annotation.Nullable;

public class SimpleContextSupplier implements ContextSupplier {
    private final String context;

    public SimpleContextSupplier(@Nullable String context) {
        this.context = context;
    }

    @Override
    public String getValue() {
        return context;
    }
}
