package com.atlassian.buildeng.ecs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ojongerius on 09/05/2016.
 */
public class SchedulerConfiguration extends Configuration {
    /*
    @Valid
    @NotNull
    @JsonProperty
    private JerseyClientConfiguration httpClient = new JerseyClientConfiguration();
    */

    // TODO: this is where we'll instantiate a client for the resource to use
    //public JerseyClientConfiguration getJerseyClientConfiguration() {
    //    return httpClient;
    //}
}
