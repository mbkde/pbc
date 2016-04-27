package com.atlassian.buildeng.spi.isolated.docker;

import java.util.List;

public interface IsolatedAgentService {
    /**
     * Start an isolated docker agent to handle the build request
     *
     * @param request - request object
     */
    void startAgent(IsolatedDockerAgentRequest request);

    List<String> getKnownDockerImages();
}
