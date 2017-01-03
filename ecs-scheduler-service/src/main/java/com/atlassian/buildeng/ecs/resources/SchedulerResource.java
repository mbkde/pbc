package com.atlassian.buildeng.ecs.resources;

import com.atlassian.buildeng.ecs.scheduling.ArnStoppedState;
import com.atlassian.buildeng.ecs.api.Scheduler;
import com.atlassian.buildeng.ecs.exceptions.ECSException;
import com.atlassian.buildeng.ecs.exceptions.ImageAlreadyRegisteredException;
import com.atlassian.buildeng.ecs.scheduling.BambooServerEnvironment;
import com.atlassian.buildeng.ecs.scheduling.DefaultSchedulingCallback;
import com.atlassian.buildeng.ecs.scheduling.ECSConfiguration;
import com.atlassian.buildeng.ecs.scheduling.ECSScheduler;
import com.atlassian.buildeng.ecs.scheduling.SchedulerBackend;
import com.atlassian.buildeng.ecs.scheduling.SchedulingRequest;
import com.atlassian.buildeng.ecs.scheduling.TaskDefinitionRegistrations;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentException;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerAgentResult;
import com.atlassian.buildeng.spi.isolated.docker.IsolatedDockerRequestCallback;
import com.codahale.metrics.annotation.Timed;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ManagedAsync;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import java.util.UUID;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;


@Path("/rest/scheduler")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SchedulerResource {

    private final TaskDefinitionRegistrations taskDefRegistrations;
    private final ECSScheduler ecsScheduler;
    private final SchedulerBackend schedulerBackend;
    private final ECSConfiguration configuration;

    @Inject
    public SchedulerResource(TaskDefinitionRegistrations taskDefReistrations, ECSScheduler ecsScheduler, SchedulerBackend schedulerBackend, ECSConfiguration configuration) {
        this.taskDefRegistrations = taskDefReistrations;
        this.ecsScheduler = ecsScheduler;
        this.schedulerBackend = schedulerBackend;
        this.configuration = configuration;
    }

    @ManagedAsync
    @POST
    @Timed
    public void schedule(final String body, @Suspended final AsyncResponse response) {
        final Scheduler s = Scheduler.fromJson(body);
        BambooServerEnvironment env = new BambooServerEnvironment() {
            @Override
            public String getCurrentSidekick() {
                return s.getSidekick();
            }

            @Override
            public String getBambooBaseUrl() {
                return s.getBambooServer();
            }

            @Override
            public String getECSTaskRoleARN() {
                return s.getTaskARN();
            }
        };
        int revision = taskDefRegistrations.findTaskRegistrationVersion(s.getConfiguration(), env);
        if (revision == -1) {
            try {
                revision = taskDefRegistrations.registerDockerImage(s.getConfiguration(), env);
            } catch (ImageAlreadyRegisteredException | ECSException ex) {
                response.resume(ex);
                return;
            }
        }
        SchedulingRequest request = new SchedulingRequest(UUID.fromString(s.getUuid()), s.getResultId(), revision, s.getConfiguration());
        DefaultSchedulingCallback dsc = new DefaultSchedulingCallback(new IsolatedDockerRequestCallback() {
            @Override
            public void handle(IsolatedDockerAgentResult result) {
                response.resume(result);
            }

            @Override
            public void handle(IsolatedDockerAgentException exception) {
                response.resume(exception);
            }
        }, s.getResultId());
        ecsScheduler.schedule(request, dsc);
        /* ^^ TODO: Dropwizard will :
        Once sayHello has returned, Jersey takes the Scheduler instance and looks for a provider class which can
        write Scheduler instances as application/json. Dropwizard has one such provider built in which allows for
        producing and consuming Java objects as JSON objects. The provider writes out the JSON and the client
        receives a 200 OK response with a content type of application/json.
         */
    }

    @GET
    @Path("stopped")
    public ArnStoppedState[] getStoppedTasks(@QueryParam("arn") List<String> arnsList) throws ECSException {
        if (arnsList == null || arnsList.isEmpty()) {
            return new ArnStoppedState[0];
        }
        Collection<ArnStoppedState> tasks = schedulerBackend.checkStoppedTasks(configuration.getCurrentCluster(), arnsList);
        return tasks.toArray(new ArnStoppedState[0]);
    }
}

