package com.atlassian.buildeng.ecs.rest;

/**
 * Created by obrent on 4/02/2016.
 */
public class GetCurrentClusterResponse {
    public String cluster;

    public GetCurrentClusterResponse(String cluster) {
        this.cluster = cluster;
    }
}
