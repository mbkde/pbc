package com.atlassian.buildeng.spi.isolated.docker;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IsolatedAgentService {
    /**
     * Start an isolated docker agent to handle the build request
     *
     * @param request - request object
     * @param callback callback to process the result
     */
    void startAgent(IsolatedDockerAgentRequest request, IsolatedDockerRequestCallback callback);

   /**
    * optional method listing all known docker images for use in the UI
    */
    default List<String> getKnownDockerImages() {
        return Collections.emptyList();
    }

    /**
     * participate in rendering of the job/plan result summary PBC page snippet.
     * @param configuration
     * @param customData
     * @return null if no logs available, html snippet otherwise
     */
    default String renderContainerLogs(Configuration configuration, Map<String, String> customData) {
        return null;
    }

}
