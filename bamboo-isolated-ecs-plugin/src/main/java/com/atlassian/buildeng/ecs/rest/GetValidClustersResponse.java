package com.atlassian.buildeng.ecs.rest;

import java.util.List;

public class GetValidClustersResponse {
    private List<String> clusters;

    public GetValidClustersResponse(List<String> clusters) {
        this.clusters = clusters;
    }
}
