/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atlassian.buildeng.ecs.exceptions;

import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public abstract class RestableIsolatedDockerException extends IsolatedDockerAgentException {

    private final Status status;
    RestableIsolatedDockerException(Status status, Exception exc) {
        super(exc);
        this.status = status;
    }

    RestableIsolatedDockerException(Status status, String message) {
        super(message);
        this.status = status;
    }

    Response toResponse() {
        return Response.status(status).
            entity(getMessage()).
            type("text/plain").
            build();
    }
}
