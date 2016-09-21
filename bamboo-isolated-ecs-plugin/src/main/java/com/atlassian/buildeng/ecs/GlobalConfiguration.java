/*
 * Copyright 2016 Atlassian.
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
package com.atlassian.buildeng.ecs;

import com.amazonaws.Request;
import com.amazonaws.protocol.json.JsonClientMetadata;
import com.amazonaws.protocol.json.SdkJsonProtocolFactory;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.HostVolumeProperties;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.MountPoint;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.Volume;
import com.amazonaws.services.ecs.model.VolumeFrom;
import com.amazonaws.services.ecs.model.transform.RegisterTaskDefinitionRequestMarshaller;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.RevisionNotActiveException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ConfigurationBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.fileupload.util.Streams;

import static com.atlassian.buildeng.ecs.Constants.AGENT_CONTAINER_NAME;
import static com.atlassian.buildeng.ecs.Constants.LAAS_CONFIGURATION;
import static com.atlassian.buildeng.ecs.Constants.LAAS_ENVIRONMENT_KEY;
import static com.atlassian.buildeng.ecs.Constants.LAAS_ENVIRONMENT_VAL;
import static com.atlassian.buildeng.ecs.Constants.LAAS_SERVICE_ID_KEY;
import static com.atlassian.buildeng.ecs.Constants.LAAS_SERVICE_ID_VAL;
import static com.atlassian.buildeng.ecs.Constants.RUN_SCRIPT;
import static com.atlassian.buildeng.ecs.Constants.SIDEKICK_CONTAINER_NAME;
import static com.atlassian.buildeng.ecs.Constants.WORK_DIR;

/**
 *
 * @author mkleint
 */
public class GlobalConfiguration {
    
    // Bandana access keys
    static String BANDANA_CLUSTER_KEY = "com.atlassian.buildeng.ecs.cluster";
    static String BANDANA_DOCKER_MAPPING_KEY = "com.atlassian.buildeng.ecs.docker.config2";
    static String BANDANA_ECS_TASK_MAPPING_KEY = "com.atlassian.buildeng.ecs.docker.task.config";
    static String BANDANA_SIDEKICK_KEY = "com.atlassian.buildeng.ecs.sidekick";
    static String BANDANA_ASG_KEY = "com.atlassian.buildeng.ecs.asg";
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalConfiguration.class);
    private final BandanaManager bandanaManager;
    private final AdministrationConfigurationAccessor admConfAccessor;

    public GlobalConfiguration(BandanaManager bandanaManager, AdministrationConfigurationAccessor admConfAccessor) {
        this.bandanaManager = bandanaManager;
        this.admConfAccessor = admConfAccessor;
    }

    public String getTaskDefinitionName() {
        return admConfAccessor.getAdministrationConfiguration().getInstanceName() + Constants.TASK_DEFINITION_SUFFIX;
    }
    
    //TODO eventually separate that out to be optional
    private ContainerDefinition withFluentDLogs(ContainerDefinition def) {
        return def.withLogConfiguration(LAAS_CONFIGURATION)
                .withEnvironment(new KeyValuePair().withName(LAAS_SERVICE_ID_KEY).withValue(LAAS_SERVICE_ID_VAL))
                .withEnvironment(new KeyValuePair().withName(LAAS_ENVIRONMENT_KEY).withValue(LAAS_ENVIRONMENT_VAL))
                .withEnvironment(new KeyValuePair().withName(Constants.ECS_CLUSTER_KEY).withValue(getCurrentCluster()));

    }

    private ContainerDefinition withSwarm(ContainerDefinition def) {
        return def.withMountPoints(new MountPoint().withContainerPath("/buildeng-swarm").withSourceVolume("swarm").withReadOnly(true));
    }

    private RegisterTaskDefinitionRequest withSwarm(RegisterTaskDefinitionRequest def) {
        return def.withVolumes(new Volume().withName("swarm").withHost(new HostVolumeProperties().withSourcePath("/buildeng/docker")));
    }

  // Constructs a standard build agent task definition request with sidekick and generated task definition family
    private RegisterTaskDefinitionRequest taskDefinitionRequest(Configuration configuration, String baseUrl, String sidekick) {
        ContainerDefinition main = withFluentDLogs(new ContainerDefinition()
                .withName(AGENT_CONTAINER_NAME)
                .withCpu(configuration.getSize().cpu())
                .withMemory(configuration.getSize().memory())
                .withImage(configuration.getDockerImage())
                .withVolumesFrom(new VolumeFrom().withSourceContainer(SIDEKICK_CONTAINER_NAME))
                .withEntryPoint(RUN_SCRIPT)
                .withWorkingDirectory(WORK_DIR)
                .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_SERVER).withValue(baseUrl))
                .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_IMAGE).withValue(configuration.getDockerImage())));
        
        RegisterTaskDefinitionRequest req = withSwarm(new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(
                        withSwarm(main),
                        Constants.SIDEKICK_DEFINITION
                                .withImage(sidekick))
                .withFamily(getTaskDefinitionName()));
        configuration.getExtraContainers().forEach((Configuration.ExtraContainer t) -> {
            ContainerDefinition d = new ContainerDefinition()
                    .withName(t.getName())
                    .withImage(t.getImage())
                    .withCpu(t.getExtraSize().cpu())
                    .withMemory(t.getExtraSize().memory())
                    .withEssential(false);
            if (isDockerInDockerImage(t.getImage())) {
                //https://hub.docker.com/_/docker/
                //TODO align storage driver with whatever we are using? (overlay)
                d.setPrivileged(Boolean.TRUE);
                main.withEnvironment(new KeyValuePair().withName("DOCKER_HOST").withValue("tcp://" + t.getName() + ":2375"));
            }
            req.withContainerDefinitions(d);
            main.withLinks(t.getName());
        });
        return req;
    }
    
    // Constructs a standard de-register request for a standard generated task definition
    private DeregisterTaskDefinitionRequest deregisterTaskDefinitionRequest(Integer revision) {
        return new DeregisterTaskDefinitionRequest().withTaskDefinition(getTaskDefinitionName() + ":" + revision);
    }

   // Sidekick management

    /**
     * Get the repository that is currently configured to be used for the agent sidekick
     *
     * @return The current sidekick repository
     */
    synchronized String getCurrentSidekick() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY);
        return name == null ? Constants.DEFAULT_SIDEKICK_REPOSITORY : name;
    }

    /**
     *  Set the agent sidekick to be used with isolated docker
     *
     * @param name The sidekick repository
     */
    synchronized void setSidekick(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SIDEKICK_KEY, name);
    }

    private void persistBandanaDockerMappingsConfiguration(Map<Configuration, Integer> dockerMappings, Map<String, Integer> taskRequestMappings) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DOCKER_MAPPING_KEY, convertToPersisted(dockerMappings));
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ECS_TASK_MAPPING_KEY, taskRequestMappings);
    }

    /**
     * Reset the agent sidekick to be used to the default
     */
    void resetSidekick() {
        setSidekick(Constants.DEFAULT_SIDEKICK_REPOSITORY);
    }

    // ECS Cluster management

    /**
     * Get the ECS cluster that is currently configured to be used
     *
     * @return The current cluster name
     */
    public synchronized String getCurrentCluster() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CLUSTER_KEY);
        return name == null ? Constants.DEFAULT_CLUSTER : name;
    }

    /**
     * Set the ECS cluster to run isolated docker agents on
     *
     * @param name The cluster name
     */
    synchronized void setCluster(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_CLUSTER_KEY, name);
    }

    /**
     * Get a collection of potential ECS clusters to use
     *
     * @return The collection of cluster names
     */
    List<String> getValidClusters() throws ECSException {
        try {
            ListClustersResult result = createClient().listClusters();
            return result.getClusterArns().stream().map((String x) -> x.split("/")[1]).collect(Collectors.toList());
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    // Docker - ECS mapping management

    /**
     * Synchronously register a docker image to be used with isolated docker builds
     *
     * @param dockerImage The image to register
     * @return The internal identifier for the registered image.
     */
    synchronized int registerDockerImage(Configuration configuration) throws ImageAlreadyRegisteredException, ECSException {
        Map<String, Integer> registrationMappings = getAllECSTaskRegistrations();
        String newReg = createRegisterTaskDefinitionString(configuration);
        if (registrationMappings.containsKey(newReg)) {
            throw new ImageAlreadyRegisteredException(configuration.getDockerImage());
        }
        
        Map<Configuration, Integer> dockerMappings = getAllRegistrations();
        Integer revision = registerDockerImageECS(configuration, getCurrentSidekick());
        dockerMappings.put(configuration, revision);
        registrationMappings.put(newReg, revision);
        persistBandanaDockerMappingsConfiguration(dockerMappings, registrationMappings);
        return revision;
    }
    
    private Integer registerDockerImageECS(Configuration configuration, String sidekick) throws ECSException {
        try {
            RegisterTaskDefinitionRequest req = taskDefinitionRequest(configuration, admConfAccessor.getAdministrationConfiguration().getBaseUrl(), sidekick);
            RegisterTaskDefinitionResult result = createClient().registerTaskDefinition(req);
            return result.getTaskDefinition().getRevision();
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }

    /**
     * Synchronously deregister the docker image with given task revision
     *
     * @param revision The internal ECS task definition to deregister
     */
    synchronized void deregisterDockerImage(Integer revision) throws RevisionNotActiveException, ECSException {
        Map<Configuration, Integer> dockerMappings = getAllRegistrations();
        if (!dockerMappings.containsValue(revision)) {
            throw new RevisionNotActiveException(revision);
        }
        deregisterDockerImageECS(revision);
        removeWithValue(revision, dockerMappings);
        Map<String, Integer> taskRegMappings = getAllECSTaskRegistrations();
        removeWithValue(revision, taskRegMappings);
        persistBandanaDockerMappingsConfiguration(dockerMappings, taskRegMappings);
    }
    
    private void deregisterDockerImageECS(Integer revision) throws ECSException {
        try {
            createClient().deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }
    
    private <T> void removeWithValue(Integer value, Map<T, Integer> map) {
        for (Iterator<Map.Entry<T, Integer>> iterator = map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<T, Integer> next = iterator.next();
            if (value.equals(next.getValue())) {
                iterator.remove();
            }
        }
    }
    
    /**
     * find task definition registration for given configuration
     * @param configuration
     * @return either the revision or -1 when not found
     */
    synchronized int findTaskRegistrationVersion(Configuration configuration) {
        String reg = createRegisterTaskDefinitionString(configuration);
        
        Integer val = getAllECSTaskRegistrations().get(reg);
        return val != null ? val : -1;
    }

    private String createRegisterTaskDefinitionString(Configuration configuration) {
        RegisterTaskDefinitionRequestMarshaller rtdm = new RegisterTaskDefinitionRequestMarshaller(new SdkJsonProtocolFactory(new JsonClientMetadata()) );
        Request<RegisterTaskDefinitionRequest> rr = rtdm.marshall(taskDefinitionRequest(configuration, admConfAccessor.getAdministrationConfiguration().getBaseUrl(), getCurrentSidekick()));
        try {
            return Streams.asString(rr.getContent(), "UTF-8");
        } catch (IOException ex) {
            logger.error("No way to turn Registration Task to string", ex);
            return null;
        }
    }

    /**
     * Returns a list of Configuration objects that were used to register the given
     * task definition revision
     * @return All the docker image:identifier pairs this service has registered
     */
    synchronized Map<Configuration, Integer> getAllRegistrations() {
        Map<String, Integer> values = (Map<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_DOCKER_MAPPING_KEY);
        return values == null ? new HashMap<>() : convertFromPersisted(values);
    }
    
    private synchronized Map<String, Integer> getAllECSTaskRegistrations() {
        Map<String, Integer> ret = (Map<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ECS_TASK_MAPPING_KEY);
        return ret == null ? new HashMap<>() : ret;
    }
    
    public synchronized String getCurrentASG() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ASG_KEY);
        return name == null ? "" : name;
    }
    
    synchronized void setCurrentASG(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_ASG_KEY, name);
    }    

    @VisibleForTesting
    AmazonECS createClient() {
        return new AmazonECSClient();
    }    

    private Map<Configuration, Integer> convertFromPersisted(Map<String, Integer> persisted) {
        final HashMap<Configuration, Integer> newMap = new HashMap<>();
        persisted.forEach((String t, Integer u) -> {
            newMap.put(load(t), u);
        });
        return newMap;
    }    
    
    private Map<String, Integer> convertToPersisted(Map<Configuration, Integer> val) {
        final HashMap<String, Integer> newMap = new HashMap<>();
        val.forEach((Configuration t, Integer u) -> {
            newMap.put(persist(t), u);
        });
        return newMap;
    }
    
    @VisibleForTesting
    String persist(Configuration conf) {
        JsonObject el = new JsonObject();
        el.addProperty("image", conf.getDockerImage());
        el.addProperty("size", conf.getSize().name());
        el.add("extraContainers", Configuration.toJson(conf.getExtraContainers()));
        return el.toString();
    }
    
    @VisibleForTesting
    Configuration load(String source) {
        JsonParser p = new JsonParser();
        JsonElement obj = p.parse(source);
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
                        Configuration.ExtraContainer extra = Configuration.from(t.getAsJsonObject());
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

    private boolean isDockerInDockerImage(String image) {
        return image.startsWith("docker:") && image.endsWith("dind");
    }
}
