package com.atlassian.buildeng.kubernetes.cluster;

import com.atlassian.buildeng.kubernetes.KubernetesClient;
import com.atlassian.buildeng.kubernetes.context.ContextSupplier;
import com.atlassian.buildeng.kubernetes.exception.ClusterRegistryKubectlException;
import com.atlassian.buildeng.kubernetes.exception.KubectlException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterFactory {
    private static final Logger logger = LoggerFactory.getLogger(ClusterFactory.class);
    private static final String CACHED_VALUE = "cachedValue";

    private final KubernetesClient kubectl;
    private final ContextSupplier globalContextSupplier;
    private final LoadingCache<String, List<ClusterRegistryItem>> cache;

    public ClusterFactory(KubernetesClient kubectl, ContextSupplier globalContextSupplier) {
        this.kubectl = kubectl;
        this.globalContextSupplier = globalContextSupplier;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build(new CacheLoader<String, List<ClusterRegistryItem>>() {
                    @Override
                    public List<ClusterRegistryItem> load(String key) throws Exception {
                        return loadClusters();
                    }
                });
    }

    public List<ClusterRegistryItem> getClusters() throws ClusterRegistryKubectlException {
        try {
            return cache.getUnchecked(CACHED_VALUE);
        } catch (UncheckedExecutionException ex) {
            if (ex.getCause() instanceof KubectlException) {
                throw new ClusterRegistryKubectlException(ex.getMessage(), ex.getCause());
            }
            logger.error("unknown failure at loading clusters from registry", ex);
            return Collections.emptyList();
        }
    }

    private List<ClusterRegistryItem> loadClusters() throws KubectlException {
        String json = kubectl.executeKubectl(globalContextSupplier, "get", "clusters", "-o", "json");
        //TODO check in future if clusterregistry.k8s.io/v1alpha1 / Cluster is supported by the client lib parsing
        JsonParser parser = new JsonParser();
        JsonElement root = parser.parse(json);
        List<ClusterRegistryItem> items = new ArrayList<>();
        if (root != null && root.isJsonObject()) {
            JsonObject rootObj = root.getAsJsonObject();
            if (rootObj.has("items") && rootObj.has("kind")) {
                JsonArray array = rootObj.getAsJsonArray("items");
                if (array != null) {
                    for (Iterator iterator = array.iterator(); iterator.hasNext(); ) {
                        JsonElement next = (JsonElement) iterator.next();
                        if (next != null
                                && next.isJsonObject()
                                && next.getAsJsonObject().has("kind")
                                && "Cluster".equals(next.getAsJsonObject()
                                .getAsJsonPrimitive("kind").getAsString())) {
                            JsonObject metadata = next.getAsJsonObject().getAsJsonObject("metadata");
                            String name = metadata.getAsJsonPrimitive("name").getAsString();
                            JsonObject labels = metadata.getAsJsonObject("labels");
                            List<Pair<String, String>> labelList = labels == null ? Collections.emptyList()
                                    : labels.entrySet().stream()
                                    .map((Map.Entry<String, JsonElement> t) ->
                                            new ImmutablePair<>(t.getKey(),
                                                    t.getValue().getAsJsonPrimitive().getAsString()))
                                    .collect(Collectors.toList());
                            items.add(new ClusterRegistryItem(name, labelList));
                        }
                    }
                }
            }
        }
        return items;
    }
}
