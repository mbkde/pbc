package com.atlassian.buildeng.ecs.rest;

public class GetCurrentClusterResponse {
    private String cluster;

    public GetCurrentClusterResponse(String cluster) {
        this.cluster = cluster;
    }
}
