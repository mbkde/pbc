/*
 * Copyright 2018 Atlassian Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationPersistence;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;

@BambooComponent
public class Validator {
    private final GlobalConfiguration globalConfiguration;

    public Validator(GlobalConfiguration globalConfiguration) {
        this.globalConfiguration = globalConfiguration;
    }

    /**
     * Validate configuration.
     * Errors are collected in ErrorCollection parameter passed in.
     */
    public void validate(String image, String size, String role, String architecture, String extraCont,
            ErrorCollection errorCollection, boolean task) {
        if (role != null) {
            if (!StringUtils.deleteWhitespace(role).equals(role)) {
                errorCollection.addError(task ? Configuration.TASK_DOCKER_AWS_ROLE : Configuration.DOCKER_AWS_ROLE,
                    "AWS IAM Role cannot contain whitespace.");
            } else if (!Pattern.compile("arn:aws:iam::[0-9]+:role/[a-zA-Z0-9+=,.@_\\-]+").matcher(role).matches()) {
                errorCollection.addError(task ? Configuration.TASK_DOCKER_AWS_ROLE : Configuration.DOCKER_AWS_ROLE,
                    "AWS IAM Role doesn't match ARN pattern.");
            }
        }

        if (StringUtils.isNotBlank(architecture) && !(globalConfiguration.getArchitectureConfig().containsKey(architecture))) {
            errorCollection.addError(task ? Configuration.TASK_DOCKER_ARCHITECTURE :
                            Configuration.DOCKER_ARCHITECTURE,
                    "Specified architecture is not supported on this server. Supported architectures: "
                            + globalConfiguration.getArchitectureConfig().keySet());
        }

        validateExtraContainers(extraCont, errorCollection);

        if (StringUtils.isBlank(image)) {
            errorCollection.addError(task ? Configuration.TASK_DOCKER_IMAGE : Configuration.DOCKER_IMAGE,
                "Docker Image cannot be empty.");
        } else if (image != null && !StringUtils.deleteWhitespace(image).equals(image)) {
            errorCollection.addError(task ? Configuration.TASK_DOCKER_IMAGE : Configuration.DOCKER_IMAGE,
                "Docker Image cannot contain whitespace.");
        }

        try {
            if (size == null) {
                errorCollection.addError(task ? Configuration.TASK_DOCKER_IMAGE_SIZE : Configuration.DOCKER_IMAGE_SIZE,
                    "Image size must be defined and one of:"
                        + Arrays.toString(Configuration.ContainerSize.values()));
            } else {
                Configuration.ContainerSize val = Configuration.ContainerSize.valueOf(size);
            }
        } catch (IllegalArgumentException e) {
            errorCollection.addError(task ? Configuration.TASK_DOCKER_IMAGE_SIZE : Configuration.DOCKER_IMAGE_SIZE,
                "Image size value to be one of:" + Arrays.toString(Configuration.ContainerSize.values()));
        }

    }
    
    //TODO a bit unfortunate that the field associated with extra containers is hidden
    // the field specific reporting is not showing at all then. So needs to be global.
    private static void validateExtraContainers(String value, ErrorCollection errorCollection) {
        if (!StringUtils.isBlank(value)) {
            try {
                JsonElement obj = JsonParser.parseString(value);
                if (!obj.isJsonArray()) {
                    errorCollection.addErrorMessage("Extra containers json needs to be an array.");
                } else {
                    JsonArray arr = obj.getAsJsonArray();
                    arr.forEach((JsonElement t) -> {
                        if (t.isJsonObject()) {
                            Configuration.ExtraContainer v2 = ConfigurationPersistence.from(t.getAsJsonObject());
                            if (v2 == null) {
                                errorCollection.addErrorMessage("wrong format for extra containers");
                            } else {
                                if (StringUtils.isBlank(v2.getName())) {
                                    errorCollection.addErrorMessage("Extra container requires a non empty name.");
                                }
                                if (!v2.getName().matches("[a-z0-9]([\\-a-z0-9]*[a-z0-9])?")) {
                                    errorCollection.addErrorMessage("Extra container name should "
                                            + "be composed of lowercase letters, numbers and - character only");
                                }
                                if (StringUtils.isBlank(v2.getImage())) {
                                    errorCollection.addErrorMessage("Extra container requires non empty image.");
                                }
                                if (!StringUtils.deleteWhitespace(v2.getImage()).equals(v2.getImage())) {
                                    errorCollection.addErrorMessage("Extra container image cannot contain whitespace.");
                                }
                                for (Configuration.EnvVariable env : v2.getEnvVariables()) {
                                    if (StringUtils.isBlank(env.getName())) {
                                        errorCollection.addErrorMessage(
                                                "Extra container requires non empty environment variable name.");
                                    }
                                }
                            }
                        } else {
                            errorCollection.addErrorMessage("wrong format for extra containers");
                        }
                    });
                }
            } catch (JsonParseException e) {
                errorCollection.addErrorMessage("Extra containers field is not valid json.");
            }
        }
    }
}
