package com.atlassian.buildeng.ecs;

import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import com.atlassian.buildeng.ecs.resources.SchedulerResource;
import com.atlassian.buildeng.ecs.resources.HeartBeatResource;
import com.atlassian.buildeng.ecs.scheduling.AWSSchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.CyclingECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by ojongerius on 09/05/2016.
 */

public class SchedulerApplication extends io.dropwizard.Application<Configuration> {

    public static final CyclingECSScheduler scheduler;
    public static final AWSSchedulerBackend schedulerBackend;
    public static final ECSConfiguration configuration;
    public static final TaskDefinitionRegistrations.Backend regsBackend;
    public static final TaskDefinitionRegistrations regs;

    static {
        ECSConfigurationImpl c = new ECSConfigurationImpl();
        configuration = c;
        regsBackend = c;
        schedulerBackend = new AWSSchedulerBackend();
        scheduler = new CyclingECSScheduler(schedulerBackend, configuration, new DummyEventPublisher());
        regs = new TaskDefinitionRegistrations(regsBackend, configuration);
    }

    public static void main(String[] args) throws Exception {
        validate(configuration);
        new SchedulerApplication().run(new String[] {"server"});
    }

    private static void validate(ECSConfiguration c) {
        if (StringUtils.isBlank(c.getCurrentASG()) ||
            StringUtils.isBlank(c.getCurrentCluster()) ||
            StringUtils.isBlank(c.getTaskDefinitionName())) {
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
        final SchedulerResource schedulerResource = new SchedulerResource();
        final HeartBeatResource heartbeatResource = new HeartBeatResource();
        environment.jersey().register(schedulerResource);
        environment.jersey().register(heartbeatResource);
    }
}
