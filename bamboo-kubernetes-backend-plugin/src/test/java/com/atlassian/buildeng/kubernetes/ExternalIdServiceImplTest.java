package com.atlassian.buildeng.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.core.BambooEntityOid;
import com.atlassian.bamboo.deployments.projects.DeploymentProject;
import com.atlassian.bamboo.deployments.projects.service.DeploymentProjectService;
import com.atlassian.bamboo.exception.NotFoundException;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanType;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdServiceImplTest {
    @Mock
    AdministrationConfigurationAccessor admConfAccessor;

    @Mock
    CachedPlanManager cachedPlanManager;

    @Mock
    DeploymentProjectService deploymentProjectService;

    @InjectMocks
    ExternalIdServiceImpl externalIdService;

    private final PlanKey TEST_PLAN_KEY = PlanKeys.getPlanKey("TEST-PLAN");
    private final ImmutablePlan TEST_PLAN = mockPlan(TEST_PLAN_KEY, 1L);
    private final PlanKey TEST_PLAN_BRANCH_KEY= PlanKeys.getPlanKey("TEST-PLAN12345");
    private final ImmutablePlan TEST_PLAN_BRANCH = mockPlan(TEST_PLAN_BRANCH_KEY, 2L);

    private final PlanKey TEST_PLAN_NOT_FOUND_KEY = PlanKeys.getPlanKey("TEST-NOTFOUND");
    private final PlanKey TEST_JOB_KEY = PlanKeys.getPlanKey("TEST-PARENT-JOB1");
    private final PlanKey TEST_PARENT_KEY = PlanKeys.getPlanKey("TEST-PARENT");
    private final ImmutableJob TEST_JOB = mockJob(TEST_JOB_KEY);

    private final Long TEST_DEPLOYMENT_ID = 12345L;
    private final DeploymentProject TEST_DEPLOYMENT = mockDeployment(TEST_DEPLOYMENT_ID);
    private final Long TEST_DEPLOYMENT_NOT_FOUND = 54321L;

    @Before
    public void setUp() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(admConfAccessor.getAdministrationConfiguration()).thenReturn(conf);
        when(admConfAccessor.getAdministrationConfiguration().getInstanceName()).thenReturn("test-bamboo");

        when(cachedPlanManager.getPlanByKey(TEST_PLAN_KEY)).thenReturn(TEST_PLAN);
        when(cachedPlanManager.getPlanByKey(TEST_PLAN_NOT_FOUND_KEY)).thenReturn(null);
        when(cachedPlanManager.getPlanByKey(TEST_JOB_KEY)).thenReturn(TEST_JOB);


        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_ID)).thenReturn(TEST_DEPLOYMENT);
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_NOT_FOUND)).thenReturn(null);

        when(TEST_PLAN_BRANCH.getMaster()).thenReturn(TEST_PLAN);
        when(TEST_PLAN_BRANCH.hasMaster()).thenReturn(true);

    }

    @Test
    public void testExternalIdWithPlan() {
        assertEquals("test-bamboo/TEST-PLAN/1",
            externalIdService.getExternalId(TEST_PLAN));
    }

    @Test
    public void testExternalIdWithDeployment() {
        assertEquals("test-bamboo/12345/1", externalIdService.getExternalId(TEST_DEPLOYMENT));
    }

    @Test
    public void testExternalIdWithPlanKey() {
        assertEquals("test-bamboo/TEST-PLAN/1", externalIdService.getExternalId(TEST_PLAN_KEY));
    }

    @Test
    public void testExternalIdWithId() {
        assertEquals("test-bamboo/12345/1", externalIdService.getExternalId(TEST_DEPLOYMENT_ID));
    }

    @Test(expected = NotFoundException.class)
    public void testPlanNotFound() {
        externalIdService.getExternalId(TEST_PLAN_NOT_FOUND_KEY);
    }

    @Test(expected = NotFoundException.class)
    public void testDeploymentNotFound() {
        externalIdService.getExternalId(TEST_DEPLOYMENT_NOT_FOUND);
    }

    @Test
    public void testJob() {
        assertEquals("test-bamboo/TEST-PARENT/1", externalIdService.getExternalId(TEST_JOB_KEY));
    }

    @Test
    public void testCorrectInstanceName() {
        when(admConfAccessor.getAdministrationConfiguration().getInstanceName()).thenReturn("Test Bamboo");
        assertEquals("test-bamboo/TEST-PLAN/1", externalIdService.getExternalId(TEST_PLAN));

    }

    @Test
    public void testExternalIdwithPlanBranch() {
        assertEquals("test-bamboo/TEST-PLAN/1", externalIdService.getExternalId(TEST_PLAN_BRANCH));
    }

    private ImmutablePlan mockPlan(PlanKey planKey, long entityOid) {
        ImmutablePlan plan = mock(ImmutablePlan.class);
        when(plan.getPlanKey()).thenReturn(planKey);
        when(plan.getOid()).thenReturn(BambooEntityOid.create(entityOid));
        when(plan.getPlanType()).thenReturn(PlanType.CHAIN);
        return plan;
    }

    private ImmutableJob mockJob(PlanKey planKey) {
        ImmutableJob job = mock(ImmutableJob.class, Mockito.withSettings().lenient());
        when(job.getPlanKey()).thenReturn(planKey);
        when(job.getPlanType()).thenReturn(PlanType.JOB);

        ImmutableChain parent = mock(ImmutableChain.class, Mockito.withSettings().lenient());
        when(parent.getPlanKey()).thenReturn(TEST_PARENT_KEY);
        when(parent.getOid()).thenReturn(BambooEntityOid.create(1L));
        when(parent.getPlanType()).thenReturn(PlanType.CHAIN);

        when(job.getParent()).thenReturn(parent);
        return job;
    }

    private DeploymentProject mockDeployment(Long deploymentId) {
        DeploymentProject deploymentProject = mock(DeploymentProject.class);
        when(deploymentProject.getId()).thenReturn(deploymentId);
        when(deploymentProject.getOid()).thenReturn(BambooEntityOid.create(1L));
        return deploymentProject;
    }
}