/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.spi.isolated.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationPersistence {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationPersistence.class);

    public static Configuration toConfiguration(String source) {
        JsonElement obj = JsonParser.parseString(source);
        if (obj.isJsonObject()) {
            JsonObject jsonobj = obj.getAsJsonObject();
            ConfigurationBuilder bld = ConfigurationBuilder.create(jsonobj.getAsJsonPrimitive("image").getAsString());
            JsonPrimitive size = jsonobj.getAsJsonPrimitive("size");
            if (size != null) {
                try {
                    bld.withImageSize(Configuration.ContainerSize.valueOf(size.getAsString()));
                } catch (IllegalArgumentException x) {
                    logger.error("Wrong size was persisted: {}", size);
                    //ok to skip and do nothing, the default value is REGULAR
                }
            }
            JsonArray arr = jsonobj.getAsJsonArray("extraContainers");
            if (arr != null) {
                arr.forEach((JsonElement t) -> {
                    if (t.isJsonObject()) {
                        Configuration.ExtraContainer extra = ConfigurationPersistence.from(t.getAsJsonObject());
                        if (extra != null) {
                            bld.withExtraContainer(extra);
                        }
                    }
                });
            }
            return bld.build();
        }
        return null;
    }

    public static Configuration.ExtraContainer from(JsonObject obj) {
        if (obj.has("name") && obj.has("image") && obj.has("size")) {
            String name = obj.getAsJsonPrimitive("name").getAsString();
            String image = obj.getAsJsonPrimitive("image").getAsString();
            String size = obj.getAsJsonPrimitive("size").getAsString();
            Configuration.ExtraContainerSize s;
            try {
                s = Configuration.ExtraContainerSize.valueOf(size);
            } catch (IllegalArgumentException x) {
                s = Configuration.ExtraContainerSize.REGULAR;
            }
            Configuration.ExtraContainer toRet = new Configuration.ExtraContainer(name, image, s);
            JsonArray commands = obj.getAsJsonArray("commands");
            if (commands != null) {
                List<String> comms = new ArrayList<>();
                commands.forEach((JsonElement t) -> {
                    comms.add(t.getAsString());
                });
                toRet.setCommands(comms);
            }
            JsonArray envvars = obj.getAsJsonArray("envVars");
            if (envvars != null) {
                List<Configuration.EnvVariable> vars = new ArrayList<>();
                envvars.forEach((JsonElement t) -> {
                    JsonObject to = t.getAsJsonObject();
                    vars.add(new Configuration.EnvVariable(to.getAsJsonPrimitive("name").getAsString(),
                            to.getAsJsonPrimitive("value").getAsString()));
                });
                toRet.setEnvVariables(vars);
            }
            return toRet;
        }
        return null;
    }

    public static List<Configuration.ExtraContainer> fromJsonString(String source) {
        List<Configuration.ExtraContainer> toRet = new ArrayList<>();
        try {
            JsonElement obj = JsonParser.parseString(source);
            if (obj.isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray();
                arr.forEach((JsonElement t) -> {
                    if (t.isJsonObject()) {
                        Configuration.ExtraContainer cont = from(t.getAsJsonObject());
                        if (cont != null) {
                            toRet.add(cont);
                        }
                    }
                });
            }
        } catch (JsonParseException ex) {
            logger.debug("failed to parse json", ex);
        }
        return toRet;
    }
    
    public static JsonObject toJson(Configuration conf) {
        JsonObject el = new JsonObject();
        el.addProperty("image", conf.getDockerImage());
        el.addProperty("size", conf.getSize().name());
        el.add("extraContainers", ConfigurationPersistence.toJson(conf.getExtraContainers()));
        return el;
    }

    public static JsonArray toJson(List<Configuration.ExtraContainer> extraContainers) {
        JsonArray arr = new JsonArray();
        extraContainers.forEach((Configuration.ExtraContainer t) -> {
            arr.add(ConfigurationPersistence.toJson(t));
        });
        return arr;
    }

    private static JsonObject toJson(Configuration.ExtraContainer extra) {
        JsonObject el = new JsonObject();
        el.addProperty("name", extra.getName());
        el.addProperty("image", extra.getImage());
        el.addProperty("size", extra.getExtraSize().name());
        if (!extra.getCommands().isEmpty()) {
            JsonArray arr = new JsonArray();
            extra.getCommands().forEach((String t) -> {
                arr.add(new JsonPrimitive(t));
            });
            el.add("commands", arr);
        }
        if (!extra.getEnvVariables().isEmpty()) {
            JsonArray arr = new JsonArray();
            extra.getEnvVariables().forEach((Configuration.EnvVariable t) -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", t.getName());
                obj.addProperty("value", t.getValue());
                arr.add(obj);
            });
            el.add("envVars", arr);
        }
        return el;
    }

}
