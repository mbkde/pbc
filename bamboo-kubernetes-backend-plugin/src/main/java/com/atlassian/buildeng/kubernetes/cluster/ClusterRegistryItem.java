package com.atlassian.buildeng.kubernetes.cluster;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class ClusterRegistryItem {
    final String name;
    final List<Pair<String, String>> labels;

    public ClusterRegistryItem(String name, List<Pair<String, String>> labels) {
        this.name = name;
        this.labels = labels;
    }

    public String getName() {
        return name;
    }

    public List<Pair<String, String>> getLabels() {
        return labels;
    }
}
