package com.atlassian.buildeng.ecs.rest;

import java.util.List;

public class GetAllImagesResponse {
    private List<DockerMapping> mappings;

    public GetAllImagesResponse(List<DockerMapping> mappings) {
        this.mappings = mappings;
    }
}
