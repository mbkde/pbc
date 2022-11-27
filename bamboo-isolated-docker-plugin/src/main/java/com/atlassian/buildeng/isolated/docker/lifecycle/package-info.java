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

/**
 * The purpose of this package is to stop and remove docker agents from
 * Bamboo after the agent has been used or is deemed not necessary.
 * Too many pathways and limited points where we can influence things, hence documenting here.
 * <p>
 * 1. a successful/failed build will be stopped in StopDockerAgentBuildProcessor that is running
 * on the agent. That's deemed a bit more reliable than sending remote messages from server.
 * Then on the server PostJobActionImpl takes over and removes the agent from db.
 * 2. when build fails for some specific reasons early on (artifact subscription failure)
 * then StopDockerAgentBuildProcessor is never called. BuildProcessorServerImpl is called though
 * and is capable of killing agent remotely + removing it from db.
 * 3. When build gets cancelled, there are 2 different cases.
 * a. when agent not yet online, we cannot do anything and item 4. takes over.
 * b. when agent is already building, neither classes from 1. not 2. are called. So
 * we add a listener on BuildCanceledEvent and kill the agent.
 * We cannot remove it as hung build killer would get confused.
 * 4. The ReaperJob is a scheduled job that looks for idle docker agents (those came online but their job is cancelled?)
 * and removes them.
 * 5. When AgentOfflineEvent is received for docker agents, we just remove it.
 * </p>
 */

package com.atlassian.buildeng.isolated.docker.lifecycle;
