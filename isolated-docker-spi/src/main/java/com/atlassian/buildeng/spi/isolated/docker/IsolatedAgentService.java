package com.atlassian.buildeng.spi.isolated.docker;

public interface IsolatedAgentService {
    /**
     * Execute the build request on an isolated docker agent
     *
     * @param request - request object
     * @return Any errors from the build process
     */
    IsolatedDockerAgentResult startInstance(IsolatedDockerAgentRequest request);
}
