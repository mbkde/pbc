package com.atlassian.buildeng.ecs.exceptions;

import com.atlassian.bamboo.utils.map.StringArrayMap;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;

/**
 * Created by obrent on 8/02/2016.
 */
public class ImageNotRegisteredException extends IsolatedDockerAgentException {
    private String dockerImage;

    public String getDockerImage() {
        return dockerImage;
    }

    public ImageNotRegisteredException(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    @Override
    public String toString() {
        return String.format("Docker image: '%s' is not registered", dockerImage);
    }
}
