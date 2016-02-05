package com.atlassian.buildeng.ecs.rest;

import java.util.List;

/**
 * Created by obrent on 4/02/2016.
 */
public class GetValidClustersResponse {
    public List<String> clusters;

    public GetValidClustersResponse(List<String> clusters) {
        this.clusters = clusters;
    }
}
