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
package com.atlassian.buildeng.isolated.docker;

import com.atlassian.bamboo.build.BuildDefinition;
import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.logger.ErrorUpdateHandler;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.events.BuildQueuedEvent;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import static org.mockito.Matchers.anyObject;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PreBuildQueuedEventListenerTest {
    @Mock
    private AgentCreationRescheduler scheduler;
    @Mock
    private IsolatedAgentService isolatedAgentService;
    @Mock
    private ErrorUpdateHandler errorUpdateHandler;
    @Mock
    private BuildQueueManager buildQueueManager;
            
    @InjectMocks
    private PreBuildQueuedEventListener listener;
    
    @Test
    public void testNonRecoverableFailure() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        
        IsolatedDockerAgentResult dockerResult = new IsolatedDockerAgentResult().withError("Error");
        when(isolatedAgentService.startAgent(anyObject())).thenReturn(dockerResult);
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, times(1)).removeBuildFromQueue(anyObject());
    }
    
    @Test
    public void testNonRecoverableException() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        
        when(isolatedAgentService.startAgent(anyObject())).thenThrow(new IsolatedDockerAgentException("throw"));
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, times(1)).removeBuildFromQueue(anyObject());
    }
    
    @Test
    public void testCancelledByUser() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.NOT_BUILT);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(anyObject());
        verify(scheduler, never()).reschedule(anyObject());
        verify(isolatedAgentService, never()).startAgent(anyObject());
    }
    
    @Test
    public void testPickedUpBySomeone() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.IN_PROGRESS);
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(anyObject());
        verify(scheduler, never()).reschedule(anyObject());
        verify(isolatedAgentService, never()).startAgent(anyObject());
    }
    
    @Test
    public void testRescheduledRecoverableFailure() throws IsolatedDockerAgentException {
        BuildContext buildContext = mockBuildContext(true, "image", LifeCycleState.QUEUED);
        
        IsolatedDockerAgentResult dockerResult = new IsolatedDockerAgentResult().withRetryRecoverable();
        when(isolatedAgentService.startAgent(anyObject())).thenReturn(dockerResult);
        
        BuildQueuedEvent event = new BuildQueuedEvent(this, buildContext);
        listener.call(event);
        verify(buildQueueManager, never()).removeBuildFromQueue(anyObject());
        verify(scheduler, times(1)).reschedule(anyObject());
    }
    
    

    private BuildContext mockBuildContext(boolean dockerEnabled, String image, LifeCycleState state) {
        BuildContext buildContext = mock(BuildContext.class);
        CurrentBuildResult result = mock(CurrentBuildResult.class);
        when(buildContext.getBuildResult()).thenReturn(result);
        BuildDefinition bd = mock(BuildDefinition.class);
        when(buildContext.getBuildDefinition()).thenReturn(bd);
//        when(buildContext.getEntityKey()).thenReturn(PlanKeys.getPlanKey("AAA-BBB"));
        Map<String, String> resultData = new HashMap<>();
        when(result.getLifeCycleState()).thenReturn(state);
        when(result.getCustomBuildData()).thenReturn(resultData);
        Map<String, String> customConfig = new HashMap<>();
        customConfig.put(Constants.ENABLED_FOR_JOB, "" + dockerEnabled);
        customConfig.put(Constants.DOCKER_IMAGE, image);
        when(bd.getCustomConfiguration()).thenReturn(customConfig);
        return buildContext;
    }

}