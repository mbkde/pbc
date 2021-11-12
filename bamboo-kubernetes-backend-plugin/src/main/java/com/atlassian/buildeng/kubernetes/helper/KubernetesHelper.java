/*
 * Copyright 2021 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes.helper;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.firstNonBlank;

public class KubernetesHelper {

    public static String getName(HasMetadata entity) {
        return entity != null ? getName(entity.getMetadata()) : null;
    }

    public static String getName(ObjectMeta entity) {
        return entity != null ? firstNonBlank(entity.getName(), getAdditionalPropertyText(entity.getAdditionalProperties(), "id"), entity.getUid()) : null;
    }

    protected static String getAdditionalPropertyText(Map<String, Object> additionalProperties, String name) {
        if (additionalProperties != null) {
            Object value = additionalProperties.get(name);
            if (value != null) {
                return value.toString();
            }
        }

        return null;
    }
}
