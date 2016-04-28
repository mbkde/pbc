package com.atlassian.buildeng.spi.isolated.docker;

import java.util.List;

public interface IsolatedAgentService {
    /**
     * Start an isolated docker agent to handle the build request
     *
     * @param request - request object
     * @param callback callback to process the result
     */
    void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback);

    List<String> getKnownDockerImages();
}
