package com.atlassian.buildeng.ecs.exceptions;

import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;

/**
 * Created by obrent on 8/02/2016.
 */
public class MissingElasticConfigException extends IsolatedDockerAgentException {
    @Override
    public String toString() {
        return "Missing Elastic Config";
    }
}
