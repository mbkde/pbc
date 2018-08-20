/*
 * Copyright 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.DefaultContainerSizeDescriptor;
import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class GlobalConfiguration implements ContainerSizeDescriptor {

    static String BANDANA_SIDEKICK_KEY = "com.atlassian.buildeng.pbc.kubernetes.sidekick";
    static String BANDANA_POD_TEMPLATE = "com.atlassian.buildeng.pbc.kubernetes.podtemplate";
    static String BANDANA_CONTAINER_SIZES = "com.atlassian.buildeng.pbc.kubernetes.containerSizes";
    static String BANDANA_POD_LOGS_URL = "com.atlassian.buildeng.pbc.kubernetes.podlogurl";
    static String BANDANA_CURRENT_CONTEXT = "com.atlassian.buildeng.pbc.kubernetes.context";
    private static final String MAIN_PREFIX = "main-";
    private static final String EXTRA_PREFIX = "extra-";

    private final BandanaManager bandanaManager;
    private final AdministrationConfigurationAccessor admConfAccessor;
    private final AuditLogService auditLogService;
    private final BambooAuthenticationContext authenticationContext;
    
    private final Map<String, Integer> cpuSizes = new HashMap<>();
    private final Map<String, Integer> memorySizes = new HashMap<>();
    private final Map<String, Integer> memoryLimitSizes = new HashMap<>();
    private final Map<String, String> labelSizes = new HashMap<>();
    private final ContainerSizeDescriptor defaults = new DefaultContainerSizeDescriptor();
    

    public GlobalConfiguration(BandanaManager bandanaManager, AuditLogService auditLogService,
                               AdministrationConfigurationAccessor admConfAccessor, 
                               BambooAuthenticationContext authenticationContext) {
        this.bandanaManager = bandanaManager;
        this.admConfAccessor = admConfAccessor;
        this.auditLogService = auditLogService;
        this.authenticationContext = authenticationContext;
    }

    public String getBambooBaseUrl() {
        return admConfAccessor.getAdministrationConfiguration().getBaseUrl();
    }
    
    public ContainerSizeDescriptor getSizeDescriptor() {
        return this;
    }
    
    /**
     * Strips characters from bamboo baseurl to conform to kubernetes label values.
     * @return value conforming to kube label value constraints.
     */
    public String getBambooBaseUrlAskKubeLabel() {
        return stripLabelValue(getBambooBaseUrl());
    }
    
    static String stripLabelValue(String value) {
        return value.replaceAll("[^-A-Za-z0-9_.]", "");
    }

    public String getCurrentSidekick() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY);
    }

    /**
     * get current context. Null value means to rely on default context.
     */
    public String getCurrentContext() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CURRENT_CONTEXT);
    }

    /**
     * Returns the template yaml file that encodes the server/cluster specific parts of pod definition.
     * @return string representation of yaml
     */
    public String getPodTemplateAsString() {
        String template = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_POD_TEMPLATE);
        if (template == null) {
            return "apiVersion: v1\n"
                  + "kind: Pod";
        }
        return template;
    }
    
    public String getPodLogsUrl() {
        return (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_POD_LOGS_URL);
    }

    /**
     * Saves changes to the configuration.
     */
    public void persist(String sidekick, String currentContext, String podTemplate, 
            String podLogUrl, String containerSizes) throws IOException {
        Preconditions.checkArgument(StringUtils.isNotBlank(sidekick), "Sidekick image is mandatory");
        Preconditions.checkArgument(StringUtils.isNotBlank(podTemplate), "Pod template is mandatory");
        Preconditions.checkArgument(StringUtils.isNotBlank(containerSizes), "Container sizes are mandatory");
        validateContainerSizes(containerSizes);
        
        if (!StringUtils.equals(sidekick, getCurrentSidekick())) {
            auditLogEntry("PBC Sidekick Image", getCurrentSidekick(), sidekick);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY, sidekick);
        }
        if (!StringUtils.equals(podTemplate, getPodTemplateAsString())) {
            auditLogEntry("PBC Kubernetes Pod Template", getPodTemplateAsString(), podTemplate);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_TEMPLATE, podTemplate);
        }
        if (!StringUtils.equals(containerSizes, getContainerSizesAsString())) {
            auditLogEntry("PBC Kubernetes Container Sizes", getContainerSizesAsString(), containerSizes);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CONTAINER_SIZES, containerSizes);
            reloadContainerSizes();
        }
        if (!StringUtils.equals(podLogUrl, getPodLogsUrl())) {
            auditLogEntry("PBC Kubernetes Container Logs URL", getPodLogsUrl(), podLogUrl);
            if (StringUtils.isBlank(podLogUrl)) {
                bandanaManager.removeValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_LOGS_URL);
            } else {
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_POD_LOGS_URL, podLogUrl);
            }
        }
        if (currentContext != null) {
            persistCurrentContext(currentContext);
        } else {
            //in this case we ignore the value and don't change it.
        }
    }

    /**
     * There is a REST endpoint to specifically update current context as this needs to be done automatically across
     * multiple Bamboo servers.
     * 
     * @param currentContext non null value of the new context, empty/blank value means reset 
     *                       to whatever is the default context
     */
    public void persistCurrentContext(String currentContext) {
        Preconditions.checkArgument(currentContext != null, "Current context is mandatory");
        if (!StringUtils.equals(currentContext, getCurrentContext())) {
            auditLogEntry("PBC Kubernetes Current Context", getCurrentContext(), currentContext);
            if (StringUtils.isNotBlank(currentContext))  {
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, 
                        BANDANA_CURRENT_CONTEXT, currentContext.trim());
            } else {
                //rely on default context in .kube/config.
                bandanaManager.removeValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CURRENT_CONTEXT);
            }
        }
    }

    private void auditLogEntry(String name, String oldValue, String newValue) {
        AuditLogEntry ent = new  AuditLogMessage(authenticationContext.getUserName(), new Date(), null, null, 
                AuditLogEntry.TYPE_FIELD_CHANGE, name, oldValue, newValue);
        auditLogService.log(ent);
    }

    String getContainerSizesAsString() throws IOException {
        String template = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_CONTAINER_SIZES);
        if (template == null) {
            try (InputStream is = getClass().getResourceAsStream("/defaultContainerSizes.json")) {
                StringWriter writer = new StringWriter();
                IOUtils.copy(is, writer, "UTF-8");
                return writer.toString();
            } 
        }
        return template;
    }

    @Override
    public synchronized int getCpu(Configuration.ContainerSize size) {
        return cpuSizes.getOrDefault(MAIN_PREFIX + size.name(), defaults.getCpu(size));
    }

    @Override
    public synchronized int getCpu(Configuration.ExtraContainerSize size) {
        return cpuSizes.getOrDefault(EXTRA_PREFIX + size.name(), defaults.getCpu(size));
    }

    @Override
    public synchronized int getMemory(Configuration.ContainerSize size) {
        return memorySizes.getOrDefault(MAIN_PREFIX + size.name(), defaults.getMemory(size));
    }

    @Override
    public synchronized int getMemory(Configuration.ExtraContainerSize size) {
        return memorySizes.getOrDefault(EXTRA_PREFIX + size.name(), defaults.getMemory(size));
    }

    @Override
    public synchronized int getMemoryLimit(Configuration.ContainerSize size) {
        return memoryLimitSizes.getOrDefault(MAIN_PREFIX + size.name(), defaults.getMemoryLimit(size));
    }

    @Override
    public synchronized int getMemoryLimit(Configuration.ExtraContainerSize size) {
        return memoryLimitSizes.getOrDefault(EXTRA_PREFIX + size.name(), defaults.getMemoryLimit(size));
    }

    @Override
    public synchronized String getLabel(Configuration.ContainerSize size) {
        return labelSizes.getOrDefault(MAIN_PREFIX + size.name(), defaults.getLabel(size));
    }

    @Override
    public synchronized String getLabel(Configuration.ExtraContainerSize size) {
        return labelSizes.getOrDefault(EXTRA_PREFIX + size.name(), defaults.getLabel(size));
    }

    private void validateContainerSizes(String containerSizes) throws IllegalArgumentException {
        JsonParser parser = new JsonParser();
        JsonElement root = parser.parse(containerSizes);
        Preconditions.checkArgument(root.isJsonObject() 
                && root.getAsJsonObject().has("main")
                && root.getAsJsonObject().has("extra"), "Required root json object with 'main' and 'extra' fields");
        JsonElement main = root.getAsJsonObject().get("main");
        Preconditions.checkArgument(main.isJsonArray(), "Field 'main' to be array  of objects");
        main.getAsJsonArray().forEach((JsonElement t) -> {
            Preconditions.checkArgument(t.isJsonObject(), "Field 'main' to contain objects");
            validateObject(t);
            Configuration.ContainerSize.valueOf(t.getAsJsonObject().getAsJsonPrimitive("name").getAsString());
        });
        JsonElement extra = root.getAsJsonObject().get("extra");
        Preconditions.checkArgument(main.isJsonArray(), "Field 'extra' to be array  of objects");
        extra.getAsJsonArray().forEach((JsonElement t) -> {
            Preconditions.checkArgument(t.isJsonObject(), "Field 'extra' to contain objects");
            validateObject(t);
            Configuration.ExtraContainerSize.valueOf(t.getAsJsonObject().getAsJsonPrimitive("name").getAsString());
        });        
    }

    private void validateObject(JsonElement t) {
        Preconditions.checkArgument(t.getAsJsonObject().has("name")
                && t.getAsJsonObject().has("cpu")
                && t.getAsJsonObject().has("memory")
                && t.getAsJsonObject().has("memoryLimit")
                && t.getAsJsonObject().has("label"),
                "name, memory, memoryLimit and label are required fields");
    }

    private synchronized void reloadContainerSizes() {
        try {
            cpuSizes.clear();
            memoryLimitSizes.clear();
            memorySizes.clear();
            labelSizes.clear();
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(getContainerSizesAsString());
            root.getAsJsonObject().getAsJsonArray("main").forEach((JsonElement t) -> {
                processEntry(t, MAIN_PREFIX);
            });
            root.getAsJsonObject().getAsJsonArray("extra").forEach((JsonElement t) -> {
                processEntry(t, EXTRA_PREFIX);
            });
        } catch (IOException ex) {
            Logger.getLogger(GlobalConfiguration.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

    private void processEntry(JsonElement t, String prefix) {
        JsonObject obj = t.getAsJsonObject();
        String name = obj.getAsJsonPrimitive("name").getAsString();
        String key = prefix + name;
        cpuSizes.put(key, obj.getAsJsonPrimitive("cpu").getAsInt());
        memorySizes.put(key, obj.getAsJsonPrimitive("memory").getAsInt());
        memoryLimitSizes.put(key, obj.getAsJsonPrimitive("memoryLimit").getAsInt());
        labelSizes.put(key, obj.getAsJsonPrimitive("label").getAsString());
    }
    
    
}
