
package com.atlassian.buildeng.spi.isolated.docker;

import com.atlassian.fugue.Either;
import com.atlassian.fugue.Maybe;

import java.util.Collection;
import java.util.Map;

public interface IsolatedAgentService {
    /**
     * Execute the build request on an isolated docker agent
     * @param request - request object
     * @return Any errors from the build
     */
    IsolatedDockerAgentResult startInstance(IsolatedDockerAgentRequest request);
}
