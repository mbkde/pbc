package com.atlassian.buildeng.kubernetes.context;

import com.atlassian.buildeng.kubernetes.GlobalConfiguration;

public class GlobalContextSupplier implements ContextSupplier {

    private GlobalConfiguration globalConfiguration;

    public GlobalContextSupplier(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }

    @Override
    public String getValue() {
        return globalConfiguration.getCurrentContext();
    }
}
