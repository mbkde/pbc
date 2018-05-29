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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Arrays;
import org.apache.commons.lang.StringUtils;

public class Validator {


    public static void validate(String image, String size, String extraCont, 
            ErrorCollection errorCollection, boolean task) {
        validateExtraContainers(extraCont, errorCollection);

        if (StringUtils.isBlank(image)) {
            errorCollection.addError(task ? Configuration.TASK_DOCKER_IMAGE : Configuration.DOCKER_IMAGE, 
                    "Docker Image cannot be empty.");
        } else if (image != null && !image.trim().equals(image)) {
            errorCollection.addError(task ? Configuration.TASK_DOCKER_IMAGE : Configuration.DOCKER_IMAGE, 
                    "Docker Image cannot contain whitespace.");
        }
        
        try {
            Configuration.ContainerSize val = Configuration.ContainerSize.valueOf(size);
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
                JsonElement obj = new JsonParser().parse(value);
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
