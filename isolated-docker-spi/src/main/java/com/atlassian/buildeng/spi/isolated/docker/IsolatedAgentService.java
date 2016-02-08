package com.atlassian.buildeng.spi.isolated.docker;

public interface IsolatedAgentService {
    /**
     * Start an isolated docker agent to handle the build request
     *
     * @param request - request object
     * @throws Exception Any bamboo related errors that prevent agent startup
     * @return Any implementation specific errors that prevent agent startup
     */
    IsolatedDockerAgentResult startAgent(IsolatedDockerAgentRequest request) throws IsolatedDockerAgentException;
}
