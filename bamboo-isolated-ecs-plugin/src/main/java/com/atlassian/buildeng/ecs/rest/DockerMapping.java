package com.atlassian.buildeng.ecs.rest;

public class DockerMapping {
    public String dockerImage;
    public Integer revision;

    public DockerMapping(String dockerImage, Integer revision) {
        this.dockerImage = dockerImage;
        this.revision = revision;
    }
}
