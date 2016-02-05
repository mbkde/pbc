package com.atlassian.buildeng.spi.isolated.docker;

public interface IsolatedAgentService {
    /**
     * Start an isolated docker agent to handle the build request
     *
     * @param request - request object
     * @return Any errors from the agent startup
     */
    IsolatedDockerAgentResult startAgent(IsolatedDockerAgentRequest request);
}
