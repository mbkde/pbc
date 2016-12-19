package com.atlassian.buildeng.ecs;

import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import com.atlassian.buildeng.ecs.resources.SchedulerResource;
import com.atlassian.buildeng.ecs.resources.HeartBeatResource;
import com.atlassian.buildeng.ecs.scheduling.AWSSchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import com.atlassian.event.api.EventPublisher;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * Created by ojongerius on 09/05/2016.
 */

public class SchedulerApplication extends io.dropwizard.Application<Configuration> {

    public SchedulerApplication() {
    }

    public static void main(String[] args) throws Exception {
        validateEnvironment();
        new SchedulerApplication().run(new String[] {"server"});
    }

    private static void validateEnvironment() {
        if (StringUtils.isBlank(System.getenv(ECSConfigurationImpl.ECS_ASG)) ||
            StringUtils.isBlank(System.getenv(ECSConfigurationImpl.ECS_CLUSTER)) ||
            StringUtils.isBlank(System.getenv(ECSConfigurationImpl.ECS_TASK_DEF))) {
            throw new IllegalStateException("Environment variables " +
                    ECSConfigurationImpl.ECS_ASG + ", " +
                    ECSConfigurationImpl.ECS_CLUSTER + ", " +
                    ECSConfigurationImpl.ECS_TASK_DEF +  " are mandatory.");
        }
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        // Nothing here yet
    }

    @Override
    public void run(Configuration configuration,
                    Environment environment) {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                //system environment mapped to @Named, sort of shortcut
                Map<String, String> props = new HashMap<>(System.getenv());
                String ddHost = System.getenv(DatadogEventPublisher.DATADOG_HOST);
                if (ddHost != null) {
                    String ddPort = StringUtils.defaultIfBlank(System.getenv(DatadogEventPublisher.DATADOG_PORT), "8125");
                    props.put(DatadogEventPublisher.DATADOG_PORT, ddPort);
                    bind(EventPublisher.class).to(DatadogEventPublisher.class);
                } else {
                    bind(EventPublisher.class).to(DummyEventPublisher.class);
                }
                bind(ECSConfiguration.class).to(ECSConfigurationImpl.class);
                bind(ECSScheduler.class).to(CyclingECSScheduler.class);
                bind(SchedulerBackend.class).to(AWSSchedulerBackend.class);
                bind(TaskDefinitionRegistrations.Backend.class).to(ECSConfigurationImpl.class);

                Names.bindProperties(binder(), props);
            }
        });
        //make sure to close datadog before stopping.
        environment.lifecycle().addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStopping(LifeCycle event) {
                EventPublisher ep = injector.getInstance(EventPublisher.class);
                if (ep instanceof Closeable) {
                    try {
                        ((Closeable) ep).close();
                    } catch (IOException ex) {
                    }
                }
            }
        });

        environment.jersey().register(injector.getInstance(SchedulerResource.class));
        environment.jersey().register(injector.getInstance(HeartBeatResource.class));
    }
}
