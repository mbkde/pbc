package com.atlassian.buildeng.ecs.exceptions;

import javax.ws.rs.core.Response;

/**
 * Created by obrent on 8/02/2016.
 */
public class ImageNotRegisteredException extends RestableIsolatedDockerException {

    public ImageNotRegisteredException(String dockerImage) {
        super(Response.Status.BAD_REQUEST, String.format("Docker image: '%s' is not registered", dockerImage));
    }
}
