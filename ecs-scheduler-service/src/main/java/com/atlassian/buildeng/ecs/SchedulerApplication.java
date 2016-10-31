package com.atlassian.buildeng.ecs;

import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import com.atlassian.buildeng.ecs.resources.SchedulerResource;
import com.atlassian.buildeng.ecs.resources.HeartBeatResource;
import com.atlassian.buildeng.ecs.scheduling.AWSSchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/**
 * Created by ojongerius on 09/05/2016.
 */

public class SchedulerApplication extends io.dropwizard.Application<SchedulerConfiguration> {

    public static CyclingECSScheduler scheduler;
    public static AWSSchedulerBackend schedulerBackend;
    public static ECSConfiguration configuration;
    public static TaskDefinitionRegistrations.Backend regsBackend;
    public static TaskDefinitionRegistrations regs;

    public static void main(String[] args) throws Exception {
        ECSConfigurationImpl c = new ECSConfigurationImpl();
        configuration = c;
        regsBackend = c;
        schedulerBackend = new AWSSchedulerBackend();
        scheduler = new CyclingECSScheduler(schedulerBackend, configuration, new DummyEventPublisher());
        regs = new TaskDefinitionRegistrations(regsBackend, configuration);
        new SchedulerApplication().run(new String[] {"server"});

    }

    @Override
    public void initialize(Bootstrap<SchedulerConfiguration> bootstrap) {
        // Nothing here yet
    }

    @Override
    public void run(SchedulerConfiguration configuration,
                    Environment environment) {
        final SchedulerResource schedulerResource = new SchedulerResource();
        final HeartBeatResource heartbeatResource = new HeartBeatResource();
        environment.jersey().register(schedulerResource);
        environment.jersey().register(heartbeatResource);
    }
}
