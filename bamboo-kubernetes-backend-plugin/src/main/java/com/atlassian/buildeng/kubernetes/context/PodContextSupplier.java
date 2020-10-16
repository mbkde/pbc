package com.atlassian.buildeng.kubernetes.context;

import com.atlassian.buildeng.kubernetes.Const;
import com.atlassian.buildeng.kubernetes.KubernetesClient;
import io.fabric8.kubernetes.api.model.Pod;

import javax.annotation.Nonnull;

public class PodContextSupplier implements ContextSupplier {

    private final Pod pod;

    public PodContextSupplier(@Nonnull Pod pod) {
        this.pod = pod;
    }

    @Override
    public String getValue() {
        return (String) pod.getAdditionalProperties().get(Const.PROP_CONTEXT);
    }
}
