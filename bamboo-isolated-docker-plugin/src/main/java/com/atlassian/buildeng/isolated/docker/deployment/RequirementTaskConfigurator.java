package com.atlassian.buildeng.isolated.docker.deployment;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskRequirementSupport;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.atlassian.buildeng.isolated.docker.lifecycle.CustomPreBuildActionImpl;
import com.google.common.collect.Sets;
import com.atlassian.struts.TextProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Arrays;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class RequirementTaskConfigurator extends AbstractTaskConfigurator implements TaskRequirementSupport
{

    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = LoggerFactory.getLogger(RequirementTaskConfigurator.class);
    private final TextProvider textProvider;

    // ---------------------------------------------------------------------------------------------------- Constructors

    private RequirementTaskConfigurator(TextProvider textProvider)
    {
        this.textProvider = textProvider;
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods

    @NotNull
    @Override
    public Map<String, String> generateTaskConfigMap(@NotNull ActionParametersMap params, @Nullable TaskDefinition previousTaskDefinition)
    {
        Map<String, String> configMap = super.generateTaskConfigMap(params, previousTaskDefinition);
        configMap.put(Configuration.TASK_DOCKER_IMAGE, params.getString(Configuration.TASK_DOCKER_IMAGE));
        configMap.put(Configuration.TASK_DOCKER_IMAGE_SIZE, params.getString(Configuration.TASK_DOCKER_IMAGE_SIZE));
        configMap.put(Configuration.TASK_DOCKER_EXTRA_CONTAINERS, params.getString(Configuration.TASK_DOCKER_EXTRA_CONTAINERS));
        return configMap;
    }

    @Override
    public void populateContextForEdit(@NotNull Map<String, Object> context, @NotNull TaskDefinition taskDefinition)
    {
        super.populateContextForEdit(context, taskDefinition);
        context.putAll(taskDefinition.getConfiguration());
        context.put(Configuration.TASK_DOCKER_IMAGE, taskDefinition.getConfiguration().get(Configuration.TASK_DOCKER_IMAGE));
        context.put("imageSizes", CustomPreBuildActionImpl.getImageSizes());
        context.put(Configuration.TASK_DOCKER_IMAGE_SIZE, taskDefinition.getConfiguration().get(Configuration.TASK_DOCKER_IMAGE_SIZE));
        context.put(Configuration.TASK_DOCKER_EXTRA_CONTAINERS, taskDefinition.getConfiguration().get(Configuration.TASK_DOCKER_EXTRA_CONTAINERS));
    }

    @Override
    public void populateContextForCreate(Map<String, Object> context) {
        super.populateContextForCreate(context);
        context.put("imageSizes", CustomPreBuildActionImpl.getImageSizes());
    }

    @Override
    public void validate(@NotNull ActionParametersMap params, @NotNull ErrorCollection errorCollection)
    {
        super.validate(params, errorCollection);
        
        String v = params.getString(Configuration.TASK_DOCKER_EXTRA_CONTAINERS);
        validateExtraContainers(v, errorCollection);

        if (StringUtils.isBlank(params.getString(Configuration.TASK_DOCKER_IMAGE)))
        {
            errorCollection.addError(Configuration.TASK_DOCKER_IMAGE, textProvider.getText("requirement.error.emptyImage"));
        }
        
        String size = params.getString(Configuration.TASK_DOCKER_IMAGE_SIZE);
        try {
            Configuration.ContainerSize val = Configuration.ContainerSize.valueOf(size);
        } catch (IllegalArgumentException e) {
            errorCollection.addError(Configuration.TASK_DOCKER_IMAGE_SIZE, "Image size value to be one of:" + Arrays.toString(Configuration.ContainerSize.values()));
        }
    }

    //TODO a bit unfortunate that the field associated with extra containers is hidden
    // the field specific reporting is not showing at all then. So needs to be global.
    public static void validateExtraContainers(String value, ErrorCollection errorCollection) {
        if (!StringUtils.isBlank(value)) {
            try {
                JsonElement obj = new JsonParser().parse(value);
                if (!obj.isJsonArray()) {
                    errorCollection.addErrorMessage("Extra containers json needs to be an array.");
                } else {
                    JsonArray arr = obj.getAsJsonArray();
                    arr.forEach((JsonElement t) -> {
                        if (t.isJsonObject()) {
                            Configuration.ExtraContainer v2 = Configuration.from(t.getAsJsonObject());
                            if (v2 == null) {
                                errorCollection.addErrorMessage("wrong format for extra containers");
                            } else {
                                if (StringUtils.isBlank(v2.getName())) {
                                    errorCollection.addErrorMessage("Extra container requires a non empty name.");
                                }
                                if (StringUtils.isBlank(v2.getImage())) {
                                    errorCollection.addErrorMessage("Extra container requires non empty image.");
                                }
                                for (Configuration.EnvVariable env : v2.getEnvVariables()) {
                                    if (StringUtils.isBlank(env.getName())) {
                                        errorCollection.addErrorMessage("Extra container requires non empty environment variable name.");
                                    }
                                }
                            }
                        } else {
                            errorCollection.addErrorMessage("wrong format for extra containers");
                        }
                    });
                }
            } catch (RuntimeException e) {
                errorCollection.addErrorMessage("Extra containers field is not valid json.");
            }
        }
    }

    @NotNull
    @Override
    public Set<Requirement> calculateRequirements(@NotNull TaskDefinition taskDefinition)
    {
        Set<Requirement> requirementSet = Sets.newHashSet();
        Configuration config = Configuration.forTaskConfiguration(taskDefinition);
        if (config.isEnabled()) {
            requirementSet.add(new RequirementImpl(Constants.CAPABILITY, false, config.getDockerImage(), true));
        }
        return requirementSet;
    }

}
