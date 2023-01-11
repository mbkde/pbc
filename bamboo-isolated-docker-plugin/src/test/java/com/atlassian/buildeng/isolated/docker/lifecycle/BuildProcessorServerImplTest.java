/*
 * Copyright 2022 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.atlassian.bamboo.specs.api.builders.pbc.ExtraContainer;
import com.atlassian.bamboo.specs.api.builders.pbc.PerBuildContainerForJob;
import com.atlassian.bamboo.specs.api.model.pbc.ExtraContainerProperties;
import com.atlassian.bamboo.specs.api.model.pbc.PerBuildContainerForJobProperties;
import com.atlassian.buildeng.isolated.docker.Validator;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class BuildProcessorServerImplTest {

    @Mock
    Validator validator;

    @InjectMocks
    BuildProcessorServerImpl buildProcessorServerImpl;

    @Test
    void toSpecsEntity() {
        // given
        HierarchicalConfiguration input = createBuildConfiguration();
        // when
        PerBuildContainerForJob actual = buildProcessorServerImpl.toSpecsEntity(input);
        // then
        PerBuildContainerForJob expected = createPBCJob();
        assertEquals(expected, actual);
    }

    @Test
    void addToBuildConfiguration() {
        HierarchicalConfiguration buildConfiguration = new HierarchicalConfiguration();
        buildConfiguration.setDelimiterParsingDisabled(true);
        PerBuildContainerForJobProperties input = createPBCJobProperties();
        // when
        buildProcessorServerImpl.addToBuildConfiguration(input, buildConfiguration);
        // then
        assertBuildConfiguration(buildConfiguration);
    }

    private HierarchicalConfiguration createBuildConfiguration() {
        HierarchicalConfiguration result = new HierarchicalConfiguration();
        result.setDelimiterParsingDisabled(true);
        result.setProperty(Configuration.ENABLED_FOR_JOB, "true");
        result.setProperty(Configuration.DOCKER_IMAGE, "abc123");
        result.setProperty(Configuration.DOCKER_IMAGE_SIZE, "LARGE_4X");
        result.setProperty(Configuration.DOCKER_AWS_ROLE, "arn:aws:iam::0:role/aws_role");
        result.setProperty(Configuration.DOCKER_ARCHITECTURE, "arm64");
        result.setProperty(Configuration.DOCKER_FEATURE_FLAGS, "[feature1, feature2]");
        result.setProperty(Configuration.DOCKER_EXTRA_CONTAINERS,
                "[{'name':'bbb','image':'bbb-image','size':'SMALL'}]");
        return result;
    }

    private PerBuildContainerForJobProperties createPBCJobProperties() {
        HashSet<String> featureFlags = new HashSet<>();
        featureFlags.add("feature1");
        featureFlags.add("feature2");
        List<ExtraContainerProperties> extraContainers = new ArrayList<>();
        extraContainers.add(new ExtraContainerProperties("bbb",
                "bbb-image",
                "SMALL",
                new ArrayList<>(),
                new ArrayList<>()));
        return new PerBuildContainerForJobProperties(true,
                "abc123",
                "LARGE_4X",
                extraContainers,
                "arn:aws:iam::0:role/aws_role",
                "arm64",
                featureFlags);
    }

    private PerBuildContainerForJob createPBCJob() {
        return new PerBuildContainerForJob()
                .enabled(true)
                .image("abc123")
                .size("LARGE_4X")
                .awsRole("arn:aws:iam::0:role/aws_role")
                .architecture("arm64")
                .withFeatureFlag("feature1")
                .withFeatureFlag("feature2")
                .extraContainers(new ExtraContainer().name("bbb").image("bbb-image").size("SMALL"));

    }

    private void assertBuildConfiguration(HierarchicalConfiguration buildConfiguration) {
        assertEquals(true, buildConfiguration.getProperty(Configuration.ENABLED_FOR_JOB));
        assertEquals("abc123", buildConfiguration.getProperty(Configuration.DOCKER_IMAGE));
        assertEquals("LARGE_4X", buildConfiguration.getProperty(Configuration.DOCKER_IMAGE_SIZE));
        assertEquals("arn:aws:iam::0:role/aws_role", buildConfiguration.getProperty(Configuration.DOCKER_AWS_ROLE));
        assertEquals("arm64", buildConfiguration.getProperty(Configuration.DOCKER_ARCHITECTURE));
        assertEquals("[\"feature2\",\"feature1\"]", buildConfiguration.getProperty(Configuration.DOCKER_FEATURE_FLAGS));
        assertEquals("[{\"name\":\"bbb\",\"image\":\"bbb-image\",\"size\":\"SMALL\"}]",
                buildConfiguration.getProperty(Configuration.DOCKER_EXTRA_CONTAINERS));
    }
}