package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response.Status;

public class ImageAlreadyRegisteredException extends RestableIsolatedDockerException {

    public ImageAlreadyRegisteredException(String dockerImage) {
        super(Status.BAD_REQUEST, String.format("Docker image '%s' is already registered.", dockerImage));
    }
}
