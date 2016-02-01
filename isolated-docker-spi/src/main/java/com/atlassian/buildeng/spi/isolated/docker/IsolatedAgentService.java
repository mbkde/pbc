
package com.atlassian.buildeng.spi.isolated.docker;

public interface IsolatedAgentService {

    public IsolatedDockerAgentResult startInstance(IsolatedDockerAgentRequest request) throws Exception;

}
