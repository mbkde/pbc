package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response;

/**
 * Created by obrent on 8/02/2016.
 */
public class ImageAlreadyRegisteredException extends RestableIsolatedDockerException {

    public ImageAlreadyRegisteredException(String dockerImage) {
        super(Response.Status.BAD_REQUEST, String.format("Docker image '%s' is already registered.", dockerImage));
    }
}
