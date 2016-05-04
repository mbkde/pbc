package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response.Status;

public class ImageNotRegisteredException extends RestableIsolatedDockerException {

    public ImageNotRegisteredException(String dockerImage) {
        super(Status.BAD_REQUEST, String.format("Docker image: '%s' is not registered", dockerImage));
    }
}
