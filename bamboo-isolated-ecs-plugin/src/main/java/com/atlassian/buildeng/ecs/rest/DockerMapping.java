package com.atlassian.buildeng.ecs.rest;

/**
 * Created by obrent on 4/02/2016.
 */
public class DockerMapping {
    public String dockerImage;
    public Integer revision;

    public DockerMapping(String dockerImage, Integer revision) {
        this.dockerImage = dockerImage;
        this.revision = revision;
    }
}
