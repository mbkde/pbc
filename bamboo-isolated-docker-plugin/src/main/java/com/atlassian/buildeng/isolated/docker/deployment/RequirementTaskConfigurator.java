package com.atlassian.buildeng.isolated.docker.deployment;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskRequirementSupport;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.buildeng.isolated.docker.Configuration;
import com.atlassian.buildeng.isolated.docker.Constants;
import com.google.common.collect.Sets;
import com.atlassian.struts.TextProvider;
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
        configMap.put(Configuration.TASK_DOCKER_ENABLE, "" + !StringUtils.isBlank(params.getString(Configuration.TASK_DOCKER_IMAGE)));
        return configMap;
    }

    @Override
    public void populateContextForEdit(@NotNull Map<String, Object> context, @NotNull TaskDefinition taskDefinition)
    {
        super.populateContextForEdit(context, taskDefinition);
        context.putAll(taskDefinition.getConfiguration());
        context.put(Configuration.TASK_DOCKER_IMAGE, taskDefinition.getConfiguration().get(Configuration.DOCKER_IMAGE));
    }

    @Override
    public void validate(@NotNull ActionParametersMap params, @NotNull ErrorCollection errorCollection)
    {
        super.validate(params, errorCollection);

        String image = params.getString(Configuration.TASK_DOCKER_IMAGE);


        if (StringUtils.isBlank(image))
        {
            errorCollection.addError(Configuration.TASK_DOCKER_IMAGE, textProvider.getText("requirement.error.emptyImage"));
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
