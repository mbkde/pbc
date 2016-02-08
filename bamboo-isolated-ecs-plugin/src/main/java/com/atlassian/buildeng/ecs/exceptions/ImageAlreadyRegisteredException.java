package com.atlassian.buildeng.ecs.exceptions;

import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;

/**
 * Created by obrent on 8/02/2016.
 */
public class ImageAlreadyRegisteredException extends IsolatedDockerAgentException {
    private String dockerImage;

    public ImageAlreadyRegisteredException(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    @Override
    public String toString() {
        return String.format("Docker image '%s' is already registered.", dockerImage);
    }
}
