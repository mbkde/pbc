package com.atlassian.buildeng.isolated.docker;

import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.annotations.VisibleForTesting;
import io.atlassian.fugue.Pair;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConfigurationOverride {
    // a system property containing a map of Docker registries to replace other Docker registries when used in
    // image names. The actual format is a comma separated list of registries, where every other registry
    // is the registry that should replace the preceding registry.
    // For example, "original.com,replacement.com,another.com,anothersreplacement.com"
    private static final String PROPERTY_DOCKER_REGISTRY_MAPPING = "pbc.docker.registry.map";

    private static final Map<String, String> registryOverrides = Collections.unmodifiableMap(getRegistryOverrides());

    /**
     * Takes an existing configuration object and applies the system property overrides to it.
     */
    public static Configuration applyOverrides(Configuration config) {
        for (Configuration.ExtraContainer e : config.getExtraContainers()) {
            e.setImage(overrideRegistry(e.getImage(), registryOverrides));
        }
        config.setDockerImage(overrideRegistry(config.getDockerImage(), registryOverrides));
        return config;
    }

    @VisibleForTesting
    static String overrideRegistry(String imageString, Map<String, String> registryMapping) {
        Pair<String, String> registryAndRepo = getRegistryAndRepo(imageString);
        if (!StringUtils.isEmpty(registryAndRepo.left())) {
            String registry = registryAndRepo.left();
            String repo = registryAndRepo.right();
            if (registryMapping.containsKey(registry)) {
                return registryMapping.get(registry) + "/" + repo;
            }
        }
        return imageString;
    }

    // In some situations, we replace the docker registry specified in plan config by system property pbc.
    // In order to hide that from end users, in job summary page, we need to replace the actual image with
    // the one configured in UI
    public static String reverseRegistryOverride(String imageString) {
        checkNotNull(imageString);
        return reverseRegistryOverride(imageString, registryOverrides);
    }

    @VisibleForTesting
    static String reverseRegistryOverride(String imageString, Map<String, String> registryMapping) {
        Pair<String, String> registryAndRepo = getRegistryAndRepo(imageString);
        if (!StringUtils.isEmpty(registryAndRepo.left())) {
            String registry = registryAndRepo.left();
            String repo = registryAndRepo.right();
            Optional<Map.Entry<String, String>> match = registryMapping
                    .entrySet()
                    .stream().filter((it) -> { return registry.equals(it.getValue());})
                    .findFirst();
            if(match.isPresent()) {
                return match.get().getKey() + "/" + repo;
            }
        }
        return imageString;
    }

    /**
     * Split a docker image in to a pare of <registry, repository>.
     * registry is empty String if the image is from dockerhub. e.g. postgres
     * @return
     */
    private static Pair<String, String> getRegistryAndRepo(String imageString) {
        String[] parts = imageString.split("/", 2);
        if (parts.length == 2 && (parts[0].contains(".") || parts[0].contains(":"))) {
            String registry = parts[0];
            String repo = parts[1];
            return Pair.pair(registry, repo);
        } else {
            return Pair.pair("", imageString);
        }
    }

    private static Map<String, String> getRegistryOverrides() {
        String stringMap = System.getProperty(PROPERTY_DOCKER_REGISTRY_MAPPING);
        if (stringMap == null) {
            return new HashMap<>();
        } else {
            return registryOverrideStringToMap(stringMap);
        }
    }

    @VisibleForTesting
    static Map<String, String> registryOverrideStringToMap(String stringMap) {
        List<String> list = Arrays.asList(stringMap.split(","));
        // don't throw an exception if list is malformed.
        if (list.size() % 2 != 0) {
            return new HashMap<>();
        }

        Iterator<String> it = list.iterator();
        Map<String, String> registryMap = new HashMap<>();
        while (it.hasNext()) {
            registryMap.put(it.next(), it.next());
        }
        return registryMap;
    }
}
