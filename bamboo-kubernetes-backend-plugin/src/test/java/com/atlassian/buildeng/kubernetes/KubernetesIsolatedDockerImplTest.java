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

package com.atlassian.buildeng.kubernetes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.kubernetes.exception.KubectlException;
import com.atlassian.buildeng.kubernetes.exception.PodLimitQuotaExceededException;
import com.atlassian.buildeng.kubernetes.jmx.KubeJmxService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentRequest;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.sal.api.features.DarkFeatureManager;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;

@ExtendWith({MockitoExtension.class})
public class KubernetesIsolatedDockerImplTest {
    @Mock
    GlobalConfiguration globalConfiguration;
    @Mock
    KubeJmxService kubeJmxService;
    @Mock
    ExecutorService executor;
    @Mock
    Scheduler scheduler;
    @Mock
    SubjectIdService subjectIdService;
    @Mock
    BandanaManager bandanaManager;
    @Mock
    DarkFeatureManager darkFeatureManager;
    @Mock
    KubernetesPodSpecList podSpecList;

    @InjectMocks
    KubernetesIsolatedDockerImpl kubernetesIsolatedDocker;

    @Test
    public void pbcShouldRetryOnExceedingQuota() {
        KubectlException ke = new PodLimitQuotaExceededException("exceeded quota");
        final AtomicBoolean retry = new AtomicBoolean(false);
        IsolatedDockerRequestCallback callback = new IsolatedDockerRequestCallback() {
            @Override
            public void handle(IsolatedDockerAgentResult result) {
                retry.set(result.isRetryRecoverable());
            }

            @Override
            public void handle(IsolatedDockerAgentException exception) {
            }
        };
        kubernetesIsolatedDocker.handleKubeCtlException(callback, ke);
        assertTrue(retry.get(), "PBC should retry on exceeding kube quota");
    }

    @Test
    public void testSubjectIdForPlan() {
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(null,
                "TEST-PLAN-JOB1",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
                0,
                "bk",
                0,
                true);

        when(subjectIdService.getSubjectId(any(PlanKey.class))).thenReturn("mock-subject-id");

        kubernetesIsolatedDocker.getSubjectId(request);
        verify(subjectIdService).getSubjectId(PlanKeys.getPlanKey("TEST-PLAN-JOB1"));
    }

    @Test
    public void testSubjectIdForDeployment() {
        IsolatedDockerAgentRequest request = new IsolatedDockerAgentRequest(null,
                "111-222-333",
                UUID.fromString("379ad7b0-b4f5-4fae-914b-070e9442c0a9"),
                0,
                "bk",
                0,
                false);

        when(subjectIdService.getSubjectId(any(Long.class))).thenReturn("mock-subject-id");
        kubernetesIsolatedDocker.getSubjectId(request);
        verify(subjectIdService).getSubjectId(111L);
    }

    @Test
    public void testPodFileDeleted() throws IOException {
        // given
        final IsolatedDockerAgentRequest request = mock(IsolatedDockerAgentRequest.class);
        final String subjectId = "subjectId";
        final IsolatedDockerRequestCallback callback = mock(IsolatedDockerRequestCallback.class);
        final File file = mock(File.class);
        final Pod pod = setupMocksForPodFileDeleted(request, subjectId, file);

        try (MockedConstruction<KubernetesClient> mocked = mockConstruction(KubernetesClient.class,
                // Could be worth moving the generation of KubeClient / createPod to its own
                // service that can be dependency injected in the same way as the others, and
                // then we don't have to take this hacky approach
                (mock, context) -> when(mock.createPod(file)).thenReturn(pod))) {
            // when
            kubernetesIsolatedDocker.exec(request, callback, subjectId);

        }
        // then
        verify(podSpecList).generate(request, subjectId);
        verify(podSpecList).cleanUp(file);
        verify(callback).handle(any(IsolatedDockerAgentResult.class));
    }
    
    // Helper functions

    private Pod setupMocksForPodFileDeleted(IsolatedDockerAgentRequest request, String subjectId, File file)
            throws IOException {
        final Pod pod = mock(Pod.class);
        final ObjectMeta podMeta = mock(ObjectMeta.class);
        final String uid = "abc123";
        when(podSpecList.generate(request, subjectId)).thenReturn(file);
        when(pod.getMetadata()).thenReturn(podMeta);
        when(podMeta.getUid()).thenReturn(uid);
        return pod;
    }

}
