package com.atlassian.buildeng.isolated.docker.yaml;

import com.atlassian.bamboo.specs.api.exceptions.PropertiesValidationException;
import com.atlassian.bamboo.specs.api.validators.common.ValidationContext;
import com.atlassian.bamboo.specs.yaml.BambooYamlParserUtils;
import com.atlassian.bamboo.specs.yaml.ListNode;
import com.atlassian.bamboo.specs.yaml.MapNode;
import com.atlassian.bamboo.specs.yaml.Node;
import com.atlassian.bamboo.specs.yaml.StringNode;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class YamlConfigParser {
    private interface YamlTags {
        String YAML_ROOT = "pbc";
        String IMAGE = "image";
        String SIZE = "size";
        String AWS_ROLE = "awsRole";
        String EXTRA_CONTAINERS = "extra-containers";
        String EXTRA_CONTAINER_NAME = "name";
        String EXTRA_CONTAINER_COMMANDS = "commands";
        String EXTRA_CONTAINER_VARIABLES = "variables";
    }

    private static final String DEFAULT_IMAGE_SIZE = Configuration.ContainerSize.REGULAR.name();

    /**
     * Parse Configuration from given YAML node.
     *
     * @param node YAML
     * @return configuration
     */
    @NotNull
    public Configuration parse(@NotNull Node node) {
        if (node instanceof MapNode) {
            MapNode mapNode = (MapNode) node;
            if (mapNode.getOptionalNode(YamlTags.YAML_ROOT).isPresent()) {
                Node pbcNode = mapNode.getNode(YamlTags.YAML_ROOT);
                if (pbcNode instanceof StringNode) {
                    final String dockerImage = validateDockerImage(((StringNode) pbcNode).get());
                    return ConfigurationBuilder.create(dockerImage)
                            .build();
                } else if (pbcNode instanceof MapNode) {
                    MapNode pbcMapNode = (MapNode) pbcNode;
                    final String dockerImage = validateDockerImage(pbcMapNode.getString(YamlTags.IMAGE).get());
                    final String sizeStr = pbcMapNode.getOptionalString(YamlTags.SIZE)
                            .map(StringNode::get)
                            .orElse(DEFAULT_IMAGE_SIZE);
                    final String awsRole = pbcMapNode.getOptionalString(YamlTags.AWS_ROLE)
                            .map(StringNode::get)
                            .orElse(null);
                    final Configuration.ContainerSize size;
                    try {
                        size = Configuration.ContainerSize.valueOf(sizeStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        final Set<String> sizeNames = Arrays.stream(Configuration.ContainerSize.values())
                                .map(Configuration.ContainerSize::name)
                                .collect(Collectors.toSet());
                        throw new PropertiesValidationException("Unsupported image size: " + sizeStr
                                + ". Supported values: "
                                + String.join(",", sizeNames));
                    }

                    //parse extra containers
                    final List<Configuration.ExtraContainer> extraContainers = new ArrayList<>();
                    pbcMapNode.getOptionalList(YamlTags.EXTRA_CONTAINERS, MapNode.class)
                            .ifPresent(containerMaps -> containerMaps.asListOf(MapNode.class).stream()
                                    .map(this::parseExtraContainer)
                                    .forEach(extraContainers::add));
                    return ConfigurationBuilder.create(dockerImage)
                            .withImageSize(size)
                            .withAwsRole(awsRole)
                            .withExtraContainers(extraContainers)
                            .build();
                }
            }
        }

        return ConfigurationBuilder.create("")
                .withEnabled(false)
                .build();
    }

    /**
     * Convert config to YAML node.
     *
     * @param configuration settings
     * @return YAML node
     */
    public Node toYaml(@NotNull Configuration configuration) {
        final Map<String, Object> config = new LinkedHashMap<>();
        config.put(YamlTags.IMAGE, configuration.getDockerImage());
            config.put(YamlTags.SIZE, configuration.getSize().name());
        if (StringUtils.isNotBlank(configuration.getAwsRole())) {
            config.put(YamlTags.AWS_ROLE, configuration.getAwsRole());
        }
        if (configuration.getExtraContainers() != null && !configuration.getExtraContainers().isEmpty()) {
            config.put(YamlTags.EXTRA_CONTAINERS, configuration.getExtraContainers().stream()
                    .map(this::convertExtraContainer)
                    .collect(Collectors.toList()));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(YamlTags.YAML_ROOT, config);
        return BambooYamlParserUtils.asNode(result, ValidationContext.of(YamlTags.YAML_ROOT));
    }

    @NotNull
    private Map<String, Object> convertExtraContainer(final Configuration.ExtraContainer extraContainer) {
        final Map<String, Object> container = new LinkedHashMap<>();
        container.put(YamlTags.EXTRA_CONTAINER_NAME, extraContainer.getName());
        container.put(YamlTags.IMAGE, extraContainer.getImage());
        container.put(YamlTags.SIZE, extraContainer.getExtraSize().name());
        if (extraContainer.getCommands() != null && !extraContainer.getCommands().isEmpty()) {
            container.put(YamlTags.EXTRA_CONTAINER_COMMANDS, extraContainer.getCommands());
        }
        if (extraContainer.getEnvVariables() != null && !extraContainer.getEnvVariables().isEmpty()) {
            container.put(YamlTags.EXTRA_CONTAINER_VARIABLES,
                    extraContainer.getEnvVariables().stream()
                            .collect(Collectors.toMap(Configuration.EnvVariable::getName, Configuration.EnvVariable::getValue))
            );
        }
        return container;
    }

    @NotNull
    private Configuration.ExtraContainer parseExtraContainer(MapNode containerMap) {
        final String name = containerMap.getString(YamlTags.EXTRA_CONTAINER_NAME).get();
        final String image = containerMap.getString(YamlTags.IMAGE).get();
        final String extraImageSizeStr = containerMap.getOptionalString(YamlTags.SIZE)
                .map(StringNode::get)
                .orElse(Configuration.ExtraContainerSize.REGULAR.name());
        final Configuration.ExtraContainerSize extraImageSize;
        try {
            extraImageSize = Configuration.ExtraContainerSize.valueOf(extraImageSizeStr);
        } catch (IllegalArgumentException e) {
            final Set<String> availableValues = Arrays.stream(Configuration.ExtraContainerSize.values())
                    .map(Configuration.ExtraContainerSize::name)
                    .collect(Collectors.toSet());
            throw new PropertiesValidationException("Unsupported image size: " + extraImageSizeStr
                    + ". Supported values: " + String.join(",", availableValues));
        }
        Configuration.ExtraContainer container = new Configuration.ExtraContainer(name, image, extraImageSize);

        List<String> commands = containerMap.getOptionalList(YamlTags.EXTRA_CONTAINER_COMMANDS, StringNode.class)
                .map(list -> list.asListOf(StringNode.class))
                .map(ListNode::stream)
                .map(nod -> nod.map(StringNode::get).collect(Collectors.toList()))
                .orElse(new ArrayList<>());
        container.setCommands(commands);
        List<Configuration.EnvVariable> variables = containerMap.getOptionalMap(YamlTags.EXTRA_CONTAINER_VARIABLES)
                .map(map -> map.getProperties().stream()
                        .map(property -> new Configuration.EnvVariable(property, map.getString(property).get()))
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());
        container.setEnvVariables(variables);
        return container;
    }

    private String validateDockerImage(String dockerImage) {
        if (StringUtils.isBlank(dockerImage)) {
            throw new PropertiesValidationException(YamlTags.IMAGE + " can't be empty");
        }
        return dockerImage;
    }
}
