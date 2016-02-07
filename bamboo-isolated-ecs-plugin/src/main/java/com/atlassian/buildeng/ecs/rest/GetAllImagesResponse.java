package com.atlassian.buildeng.ecs.rest;

import java.util.List;

/**
 * Created by obrent on 4/02/2016.
 */
public class GetAllImagesResponse {
    public List<DockerMapping> mappings;

    public GetAllImagesResponse(List<DockerMapping> mappings) {
        this.mappings = mappings;
    }
}
