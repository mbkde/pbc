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
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SubjectIdServiceImplTest {
    @Mock
    AdministrationConfigurationAccessor admConfAccessor;

    @Mock
    CachedPlanManager cachedPlanManager;

    @Mock
    DeploymentProjectService deploymentProjectService;

    @InjectMocks
    SubjectIdServiceImpl subjectIdService;

    private final PlanKey TEST_PLAN_KEY = PlanKeys.getPlanKey("TEST-PLAN");
    private final ImmutablePlan TEST_PLAN = mockPlan(TEST_PLAN_KEY, 1L);
    private final PlanKey TEST_PLAN_BRANCH_KEY= PlanKeys.getPlanKey("TEST-PLAN12345");
    private final ImmutablePlan TEST_PLAN_BRANCH = mockPlan(TEST_PLAN_BRANCH_KEY, 2L);
    private final PlanKey TEST_VERY_LONG_PLAN_KEY = PlanKeys.getPlanKey("TESTTHISISSUSPICIOUSLYLONG-PLANTOOOOOLONGTOBEREALWHYISITSOLONG");
    private final ImmutablePlan TEST_VERY_LONG_PLAN = mockPlan(TEST_VERY_LONG_PLAN_KEY, 1234566789L);


    private final PlanKey TEST_PLAN_NOT_FOUND_KEY = PlanKeys.getPlanKey("TEST-NOTFOUND");
    private final PlanKey TEST_JOB_KEY = PlanKeys.getPlanKey("TEST-PARENT-JOB1");
    private final PlanKey TEST_PARENT_KEY = PlanKeys.getPlanKey("TEST-PARENT");
    private final ImmutableJob TEST_JOB = mockJob(TEST_JOB_KEY, 1L);

    private final Long TEST_DEPLOYMENT_ID = 12345L;
    private final DeploymentProject TEST_DEPLOYMENT = mockDeployment(TEST_PLAN_KEY,TEST_DEPLOYMENT_ID);
    private final Long TEST_DEPLOYMENT_ID_LONG_PLAN_KEY = 987L;
    private final DeploymentProject TEST_DEPLOYMENT_LONG_PLAN_KEY = mockDeployment(TEST_VERY_LONG_PLAN_KEY, TEST_DEPLOYMENT_ID_LONG_PLAN_KEY);
    private final Long TEST_DEPLOYMENT_NOT_FOUND = 54321L;

    private static final Integer IAM_REQUEST_LIMIT = 63;

    @Before
    public void setUp() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(admConfAccessor.getAdministrationConfiguration()).thenReturn(conf);
        when(admConfAccessor.getAdministrationConfiguration().getInstanceName()).thenReturn("test-bamboo");

        when(cachedPlanManager.getPlanByKey(TEST_PLAN_KEY)).thenReturn(TEST_PLAN);
        when(cachedPlanManager.getPlanByKey(TEST_PLAN_NOT_FOUND_KEY)).thenReturn(null);
        when(cachedPlanManager.getPlanByKey(TEST_JOB_KEY)).thenReturn(TEST_JOB);
        when(cachedPlanManager.getPlanByKey(TEST_VERY_LONG_PLAN_KEY)).thenReturn(TEST_VERY_LONG_PLAN);


        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_ID)).thenReturn(TEST_DEPLOYMENT);
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_NOT_FOUND)).thenReturn(null);
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_ID_LONG_PLAN_KEY)).thenReturn(TEST_DEPLOYMENT_LONG_PLAN_KEY);

        when(TEST_PLAN_BRANCH.getMaster()).thenReturn(TEST_PLAN);
        when(TEST_PLAN_BRANCH.hasMaster()).thenReturn(true);

    }

    @Test
    public void testSubjectIdWithPlan() {
        assertEquals("test-bamboo/TEST-PLAN/B/1",
            subjectIdService.getSubjectId(TEST_PLAN));
    }

    @Test
    public void testSubjectIdWithDeployment() {
        assertEquals("test-bamboo/TEST-PLAN/D/12345", subjectIdService.getSubjectId(TEST_DEPLOYMENT));
    }

    @Test
    public void testSubjectIdWithPlanKey() {
        assertEquals("test-bamboo/TEST-PLAN/B/1", subjectIdService.getSubjectId(TEST_PLAN_KEY));
    }

    @Test
    public void testSubjectIdWithId() {
        assertEquals("test-bamboo/TEST-PLAN/D/12345", subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID));
    }

    @Test(expected = NotFoundException.class)
    public void testPlanNotFound() {
        subjectIdService.getSubjectId(TEST_PLAN_NOT_FOUND_KEY);
    }

    @Test(expected = NotFoundException.class)
    public void testDeploymentNotFound() {
        subjectIdService.getSubjectId(TEST_DEPLOYMENT_NOT_FOUND);
    }

    @Test
    public void testJobKey() {
        assertEquals("test-bamboo/TEST-PARENT/B/1", subjectIdService.getSubjectId(TEST_JOB_KEY));
    }

    @Test
    public void testJob() {
        assertEquals("test-bamboo/TEST-PARENT/B/1", subjectIdService.getSubjectId(TEST_JOB));
    }

    @Test
    public void testCorrectInstanceName() {
        when(admConfAccessor.getAdministrationConfiguration().getInstanceName()).thenReturn("Test Bamboo");
        assertEquals("test-bamboo/TEST-PLAN/B/1", subjectIdService.getSubjectId(TEST_PLAN));

    }

    @Test
    public void testSubjectIdWithPlanBranch() {
        assertEquals("test-bamboo/TEST-PLAN/B/1", subjectIdService.getSubjectId(TEST_PLAN_BRANCH));
    }

    @Test
    public void testVeryLongPlanKey() {
        assertEquals("test-bamboo/TESTTHISISSUSPICIOUSLYLONG-PLANTOOOOOL/B/1234566789", subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY));
        assert(subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY).length() <= IAM_REQUEST_LIMIT);

        assertEquals("test-bamboo/TESTTHISISSUSPICIOUSLYLONG-PLANTOOOOOLONGTOBE/D/987", subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID_LONG_PLAN_KEY));
        assert(subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID_LONG_PLAN_KEY).length() <= IAM_REQUEST_LIMIT);
    }

    @Test
    public void testLongInstanceName() {
        when(admConfAccessor.getAdministrationConfiguration().getInstanceName()).thenReturn("this-is-a-very-long-instance-name-this-is-way-too-long-who-would-make-a-name-this-long");
        assertEquals("this-is-a-very-long-instance-name-this-is-way-too-long-/D/12345", subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID));
        assert(subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID).length() <= IAM_REQUEST_LIMIT);
        assertEquals("this-is-a-very-long-instance-name-this-is-way-too-/B/1234566789", subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY));
        assert(subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY).length() <= IAM_REQUEST_LIMIT);
    }

    private ImmutablePlan mockPlan(PlanKey planKey, long planId) {
        ImmutablePlan plan = mock(ImmutablePlan.class);
        when(plan.getPlanKey()).thenReturn(planKey);
        when(plan.getId()).thenReturn(planId);
        when(plan.getPlanType()).thenReturn(PlanType.CHAIN);
        return plan;
    }

    private ImmutableJob mockJob(PlanKey planKey, long planId) {
        ImmutableJob job = mock(ImmutableJob.class, Mockito.withSettings().lenient());
        when(job.getPlanKey()).thenReturn(planKey);
        when(job.getPlanType()).thenReturn(PlanType.JOB);

        ImmutableChain parent = mock(ImmutableChain.class, Mockito.withSettings().lenient());
        when(parent.getPlanKey()).thenReturn(TEST_PARENT_KEY);
        when(parent.getOid()).thenReturn(BambooEntityOid.create(1L));
        when(parent.getPlanType()).thenReturn(PlanType.CHAIN);
        when(parent.getId()).thenReturn(planId);

        when(job.getParent()).thenReturn(parent);
        return job;
    }

    private DeploymentProject mockDeployment(PlanKey planKey, Long deploymentId) {
        DeploymentProject deploymentProject = mock(DeploymentProject.class);
        when(deploymentProject.getId()).thenReturn(deploymentId);
        when(deploymentProject.getPlanKey()).thenReturn(planKey);
        return deploymentProject;
    }
}