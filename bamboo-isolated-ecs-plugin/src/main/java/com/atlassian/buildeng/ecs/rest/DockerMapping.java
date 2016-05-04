package com.atlassian.buildeng.ecs.rest;

public class DockerMapping {
    private String dockerImage;
    private Integer revision;

    public DockerMapping(String dockerImage, Integer revision) {
        this.dockerImage = dockerImage;
        this.revision = revision;
    }
}
