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

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.exceptions.RevisionNotActiveException;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 *
 * @author mkleint
 */
public class GlobalConfiguration {
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


  // Constructs a standard build agent task definition request with sidekick and generated task definition family
    private RegisterTaskDefinitionRequest taskDefinitionRequest(Configuration configuration, String baseUrl, String sidekick) {
        return new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(
                        Constants.AGENT_BASE_DEFINITION
                            .withImage(configuration.getDockerImage())
                            .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_SERVER).withValue(baseUrl))
                            .withEnvironment(new KeyValuePair().withName(Constants.ENV_VAR_IMAGE).withValue(configuration.getDockerImage())),
                        Constants.SIDEKICK_DEFINITION
                            .withImage(sidekick))
                .withFamily(getTaskDefinitionName());
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
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_SIDEKICK_KEY);
        return name == null ? Constants.DEFAULT_SIDEKICK_REPOSITORY : name;
    }

    /**
     *  Set the agent sidekick to be used with isolated docker
     *
     * @param name The sidekick repository
     */
    synchronized Collection<Exception> setSidekick(String name) {
        ConcurrentMap<Configuration, Integer> dockerMappings = getAllRegistrations();
        Collection<Exception> exceptions = new ArrayList<>();
        for (Entry<Configuration, Integer> entry: Maps.newHashMap(dockerMappings).entrySet()) {
            Configuration configuration = entry.getKey();
            Integer revision = entry.getValue();
            try {
                Integer newRev = registerDockerImageECS(configuration, name);
                dockerMappings.put(configuration, newRev);
            } catch (ECSException e) {
                exceptions.add(e);
                dockerMappings.remove(configuration);
                logger.error("Error on re-registering image " + configuration.getDockerImage(), e);
            } finally {
                try {
                    deregisterDockerImageECS(revision);
                } catch (ECSException e) {
                    exceptions.add(e);
                    logger.error("Error on deregistering image " + configuration, e);
                }
            }
        }
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY_new, dockerMappings);
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_SIDEKICK_KEY, name);
        return exceptions;
    }

    /**
     * Reset the agent sidekick to be used to the default
     */
    Collection<Exception> resetSidekick() {
        return setSidekick(Constants.DEFAULT_SIDEKICK_REPOSITORY);
    }

    // ECS Cluster management

    /**
     * Get the ECS cluster that is currently configured to be used
     *
     * @return The current cluster name
     */
    public synchronized String getCurrentCluster() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_CLUSTER_KEY);
        return name == null ? Constants.DEFAULT_CLUSTER : name;
    }

    /**
     * Set the ECS cluster to run isolated docker agents on
     *
     * @param name The cluster name
     */
    synchronized void setCluster(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_CLUSTER_KEY, name);
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
    synchronized Integer registerDockerImage(Configuration configuration) throws ImageAlreadyRegisteredException, ECSException {
        ConcurrentMap<Configuration, Integer> dockerMappings = getAllRegistrations();
        if (dockerMappings.containsKey(configuration)) {
            throw new ImageAlreadyRegisteredException(configuration.getDockerImage());
        }
        
        Integer revision = registerDockerImageECS(configuration, getCurrentSidekick());
        dockerMappings.put(configuration, revision);
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY_new, dockerMappings);
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
        ConcurrentMap<Configuration, Integer> dockerMappings = getAllRegistrations();
        if (!dockerMappings.containsValue(revision)) {
            throw new RevisionNotActiveException(revision);
        }
        deregisterDockerImageECS(revision);
        //TODO with configuration objects no longer viable solution to remoe just values.
        dockerMappings.values().remove(revision);
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY_new, dockerMappings);
    }
    
    private void deregisterDockerImageECS(Integer revision) throws ECSException {
        try {
            createClient().deregisterTaskDefinition(deregisterTaskDefinitionRequest(revision));
        } catch (Exception e) {
            throw new ECSException(e);
        }
    }
    
    synchronized Integer findTaskRegistrationVersion(Configuration configuration) {
        return getAllRegistrations().get(configuration);
    }

    /**
     * @return All the docker image:identifier pairs this service has registered
     */
    synchronized ConcurrentMap<Configuration, Integer> getAllRegistrations() {
        ConcurrentHashMap<Configuration, Integer> values = (ConcurrentHashMap<Configuration, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY_new);
        if (values == null) {
            //check the old mappings. TODO remove
            ConcurrentHashMap<String, Integer> compat = (ConcurrentHashMap<String, Integer>) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY);
            if (compat != null) {
                values = convert(compat);
                bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY_new, values);
                bandanaManager.removeValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_DOCKER_MAPPING_KEY);
            }
        }
        return values != null ? values : new ConcurrentHashMap<>();
    }
    
    public synchronized String getCurrentASG() {
        String name = (String) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_ASG_KEY);
        return name == null ? "" : name;
    }
    
    synchronized void setCurrentASG(String name) {
        bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, Constants.BANDANA_ASG_KEY, name);
    }    

    @VisibleForTesting
    AmazonECS createClient() {
        return new AmazonECSClient();
    }    

    private ConcurrentHashMap<Configuration, Integer> convert(ConcurrentHashMap<String, Integer> compat) {
        final ConcurrentHashMap<Configuration, Integer> newMap = new ConcurrentHashMap<>();
        compat.forEach((String t, Integer u) -> {
            newMap.put(Configuration.of(t), u);
        });
        return newMap;
    }
    
}
