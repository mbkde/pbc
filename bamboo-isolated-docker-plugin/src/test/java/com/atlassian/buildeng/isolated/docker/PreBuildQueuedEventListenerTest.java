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

package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.agent.AgentSecurityTokenService;
import com.atlassian.buildeng.isolated.docker.events.DockerAgentFailEvent;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildKey;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.events.BuildQueuedEvent;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.isolated.docker.jmx.JMXAgentsService;
import com.atlassian.buildeng.isolated.docker.sox.DockerSoxService;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.buildeng.spi.isolated.docker.ContainerSizeDescriptor;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.atlassian.event.api.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
public class PreBuildQueuedEventListenerTest {
    @Mock
    private AgentCreationReschedulerImpl scheduler;
    @Mock
    private IsolatedAgentService isolatedAgentService;
    @Mock
    private ErrorUpdateHandler errorUpdateHandler;
    @Mock
    private BuildQueueManager buildQueueManager;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private DockerSoxService dockerSoxService;
    @Mock
    private JMXAgentsService jmx;
    @Mock
    private AgentLicenseLimits agentLicenseLimits;
    @Mock
    private ContainerSizeDescriptor sizeDescriptor;
    @Mock
    private AgentCreationLimits agentCreationLimits;
    @Mock
    private AgentsThrottled agentsThrottled;
    @Mock
    private GlobalConfiguration globalConfiguration;
    @Mock
    private AgentSecurityTokenService agentSecurityTokenService;

    @InjectMocks
    private PreBuildQueuedEventListener listener;

    @BeforeEach
    public void mockFlags() {
        when(dockerSoxService.checkSoxCompliance(any())).thenReturn(Boolean.TRUE);
        when(globalConfiguration.getEnabledProperty()).thenReturn(Boolean.TRUE);
    }

    @Test
    public void testNonRecoverableFailure() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        Mockito.doAnswer(invocation -> {
            IsolatedDockerRequestCallback cb = invocation.getArgument(1);
            cb.handle(new IsolatedDockerAgentResult().withError("Error"));
            return null;
        }).when(isolatedAgentService).startAgent(any(), any());
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, times(1)).removeBuildFromQueue(any());
        Assertions.assertEquals("Error", buildContext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR));
    }
    
    @Test
    public void testNonRecoverableException() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        
        Mockito.doAnswer(invocation -> {
            IsolatedDockerRequestCallback cb = invocation.getArgument(1);
            cb.handle(new IsolatedDockerAgentException("throw"));
            return null;
        }).when(isolatedAgentService).startAgent(any(), any());
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, times(1)).removeBuildFromQueue(any());
    }

    @Test
    public void testWhenGlobalPropertyDisabled() {
        final BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        when(globalConfiguration.getEnabledProperty()).thenReturn(Boolean.FALSE);

        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);

        verify(buildQueueManager, times(1)).removeBuildFromQueue(any());
        verify(eventPublisher, times(1)).publish(any(DockerAgentFailEvent.class));
    }

    @Test
    public void testCancelledByUser() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.NOT_BUILT);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(any());
        verify(scheduler, never()).reschedule(any());
        verify(isolatedAgentService, never()).startAgent(any(), any());
    }
    
    @Test
    public void testPickedUpBySomeone() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.IN_PROGRESS);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(any());
        verify(scheduler, never()).reschedule(any());
        verify(isolatedAgentService, never()).startAgent(any(), any());
    }
    
    @Test
    public void testRescheduledRecoverableFailure() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        when(scheduler.reschedule(any())).thenReturn(Boolean.TRUE);
        Mockito.doAnswer(invocation -> {
            IsolatedDockerRequestCallback cb = invocation.getArgument(1);
            cb.handle(new IsolatedDockerAgentResult().withRetryRecoverable("error"));
            return null;
        }).when(isolatedAgentService).startAgent(any(), any());
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(any());
        verify(scheduler, times(1)).reschedule(any());
        Assertions.assertNull(buildContext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR));
    }
    
    @Test
    public void testRerunAfterFailure() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);

        Mockito.doAnswer(invocation -> {
            IsolatedDockerRequestCallback cb = invocation.getArgument(1);
            cb.handle(new IsolatedDockerAgentResult().withError("Error"));
            return null;
        }).when(isolatedAgentService).startAgent(any(), any());
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, times(1)).removeBuildFromQueue(any());
        Assertions.assertEquals("Error", buildContext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR));
        
        //now check the rerun
        Mockito.doAnswer(invocation -> {
            IsolatedDockerRequestCallback cb = invocation.getArgument(1);
            cb.handle(new IsolatedDockerAgentResult());
            return null;
        }).when(isolatedAgentService).startAgent(any(), any());

        when(buildContext.getBuildKey()).thenReturn(new BuildKey());
        event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        Assertions.assertNotEquals("Error", buildContext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR));
    }
    
    @Test
    public void testRerunAfterFailureWithoutDocker() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        
        Mockito.doAnswer(invocation -> {
            IsolatedDockerRequestCallback cb = invocation.getArgument(1);
            cb.handle(new IsolatedDockerAgentResult().withError("Error1"));
            return null;
        }).when(isolatedAgentService).startAgent(any(), any());
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, times(1)).removeBuildFromQueue(any());
        Assertions.assertEquals("Error1", buildContext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR));
        
        //now check the rerun
        buildContext.getBuildDefinition().getCustomConfiguration().put(Configuration.ENABLED_FOR_JOB, "false");
        Assertions.assertEquals("false", buildContext.getBuildDefinition().getCustomConfiguration().get(Configuration.ENABLED_FOR_JOB));

        when(buildContext.getBuildKey()).thenReturn(new BuildKey());
        event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        Assertions.assertNotEquals("Error1", buildContext.getCurrentResult().getCustomBuildData().get(Constants.RESULT_ERROR));
        Assertions.assertNull(buildContext.getCurrentResult().getCustomBuildData().get(Configuration.ENABLED_FOR_JOB));
        Assertions.assertNull(buildContext.getCurrentResult().getCustomBuildData().get(Configuration.DOCKER_IMAGE));
    }
  
    @Test
    public void testLicenseLimitReached() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        when(agentLicenseLimits.licenseLimitReached(any())).thenReturn(Boolean.TRUE);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(any());
        //well, actually called but inside agentLicenseLimits component.
        verify(scheduler, never()).reschedule(any());
        verify(isolatedAgentService, never()).startAgent(any(), any());
    }

    @Test
    public void testAgentCreationLimitReached() {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        when(agentCreationLimits.creationLimitReached()).thenReturn(Boolean.TRUE);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(any());
        verify(scheduler).reschedule(any());
        verify(agentsThrottled).add(event.getContext().getResultKey().getKey());
        verify(jmx).recalculateThrottle(agentsThrottled);
    }

    @Test
    public void testCancelledBuildIsRemovedFromAgentsThrottledQueue() {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.NOT_BUILT);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(agentsThrottled).remove(event.getContext().getResultKey().getKey());
    }

    private BuildContext mockBuildContext(boolean dockerEnabled, String image, LifeCycleState state) {
        BuildContext buildContext = mock(BuildContext.class, Mockito.withSettings().lenient());
        CurrentBuildResult result = mock(CurrentBuildResult.class);
        when(buildContext.getCurrentResult()).thenReturn(result);
        BuildDefinition bd = mock(BuildDefinition.class);
        when(buildContext.getBuildDefinition()).thenReturn(bd);
        when(buildContext.getResultKey()).thenReturn(PlanKeys.getPlanResultKey("AAA-BBB-CCC-1"));
        Map<String, String> resultData = new HashMap<>();
        Mockito.lenient().when(result.getLifeCycleState()).thenReturn(state);
        when(result.getCustomBuildData()).thenReturn(resultData);
        Map<String, String> customConfig = new HashMap<>();
        customConfig.put(Configuration.ENABLED_FOR_JOB, "" + dockerEnabled);
        customConfig.put(Configuration.DOCKER_IMAGE, image);
        when(bd.getCustomConfiguration()).thenReturn(customConfig);
        when(buildContext.getBuildKey()).thenReturn(new BuildKey());

        BuildContext parentBuildContext = mock(BuildContext.class);
        when(buildContext.getParentBuildContext()).thenReturn(parentBuildContext);
        return buildContext;
    }

}
