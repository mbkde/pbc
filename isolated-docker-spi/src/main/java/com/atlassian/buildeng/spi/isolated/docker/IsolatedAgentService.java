
package com.atlassian.buildeng.spi.isolated.docker;

import com.atlassian.fugue.Either;
import com.atlassian.fugue.Maybe;

public interface IsolatedAgentService {

    IsolatedDockerAgentResult startInstance(IsolatedDockerAgentRequest request) throws Exception;
    Either<String, Integer> registerDockerImage(String dockerImage);
    Maybe<String> deregisterDockerImage(Integer revision);

}
