package com.atlassian.buildeng.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
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
    private final PlanKey TEST_PLAN_BRANCH_KEY = PlanKeys.getPlanKey("TEST-PLAN12345");
    private final ImmutablePlan TEST_PLAN_BRANCH = mockPlan(TEST_PLAN_BRANCH_KEY, 2L);
    private final PlanKey TEST_VERY_LONG_PLAN_KEY =
            PlanKeys.getPlanKey("TESTTHISISSUSPICIOUSLYLONG-PLANTOOOOOLONGTOBEREALWHYISITSOLONG");
    private final ImmutablePlan TEST_VERY_LONG_PLAN = mockPlan(TEST_VERY_LONG_PLAN_KEY, 1234566789L);

    private final PlanKey TEST_PLAN_NOT_FOUND_KEY = PlanKeys.getPlanKey("TEST-NOTFOUND");
    private final PlanKey TEST_JOB_PARENT_KEY = PlanKeys.getPlanKey("TEST-PARENT-JOB1");
    private final PlanKey TEST_JOB_MASTER_KEY = PlanKeys.getPlanKey("TEST-MASTER-JOB1");
    private final PlanKey TEST_PARENT_KEY = PlanKeys.getPlanKey("TEST-PARENT");
    private final PlanKey TEST_MASTER_PARENT_KEY = PlanKeys.getPlanKey("TEST-MASTER");
    private final ImmutableJob TEST_JOB = mockJob(TEST_JOB_PARENT_KEY, 1L);
    private final ImmutableJob TEST_BRANCH_JOB = mockBranchJob(TEST_JOB_MASTER_KEY, 1L);

    private final Long TEST_DEPLOYMENT_ID = 12345L;
    private final DeploymentProject TEST_DEPLOYMENT = mockDeployment(TEST_PLAN_KEY, TEST_DEPLOYMENT_ID);
    private final Long TEST_DEPLOYMENT_ID_LONG_PLAN_KEY = 987L;
    private final DeploymentProject TEST_DEPLOYMENT_LONG_PLAN_KEY =
            mockDeployment(TEST_VERY_LONG_PLAN_KEY, TEST_DEPLOYMENT_ID_LONG_PLAN_KEY);
    private final Long TEST_DEPLOYMENT_NOT_FOUND = 54321L;

    private static final Integer IAM_REQUEST_LIMIT = 63;

    @BeforeEach
    public void setUp() {
        AdministrationConfiguration conf = mock(AdministrationConfiguration.class);
        when(admConfAccessor.getAdministrationConfiguration()).thenReturn(conf);

        // used by every test except testDeploymentNotFound and testPlanNotFound
        Mockito.lenient()
                .when(admConfAccessor.getAdministrationConfiguration().getInstanceName())
                .thenReturn("test-bamboo");

        when(TEST_PLAN_BRANCH.getMaster()).thenReturn(TEST_PLAN);
        when(TEST_PLAN_BRANCH.hasMaster()).thenReturn(true);
    }

    @Test
    public void testSubjectIdWithPlan() {
        assertEquals("test-bamboo/TEST-PLAN/B/1", subjectIdService.getSubjectId(TEST_PLAN));
    }

    @Test
    public void testSubjectIdWithDeployment() {
        assertEquals("test-bamboo/TEST-PLAN/D/12345", subjectIdService.getSubjectId(TEST_DEPLOYMENT));
    }

    @Test
    public void testSubjectIdWithPlanKey() {
        when(cachedPlanManager.getPlanByKey(TEST_PLAN_KEY)).thenReturn(TEST_PLAN);

        assertEquals("test-bamboo/TEST-PLAN/B/1", subjectIdService.getSubjectId(TEST_PLAN_KEY));
    }

    @Test
    public void testSubjectIdWithId() {
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_ID)).thenReturn(TEST_DEPLOYMENT);

        assertEquals("test-bamboo/TEST-PLAN/D/12345", subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID));
    }

    @Test
    public void testPlanNotFound() {
        when(cachedPlanManager.getPlanByKey(TEST_PLAN_NOT_FOUND_KEY)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> {
            subjectIdService.getSubjectId(TEST_PLAN_NOT_FOUND_KEY);
        });
    }

    @Test
    public void testDeploymentNotFound() {
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_NOT_FOUND))
                .thenReturn(null);

        assertThrows(NotFoundException.class, () -> {
            subjectIdService.getSubjectId(TEST_DEPLOYMENT_NOT_FOUND);
        });
    }

    @Test
    public void testJobKeyReturnsParentId() {
        when(cachedPlanManager.getPlanByKey(TEST_JOB_PARENT_KEY)).thenReturn(TEST_JOB);

        assertEquals("test-bamboo/TEST-PARENT/B/1", subjectIdService.getSubjectId(TEST_JOB_PARENT_KEY));
    }

    @Test
    public void testBranchJobKeyReturnsMasterPlanId() {
        when(cachedPlanManager.getPlanByKey(TEST_JOB_MASTER_KEY)).thenReturn(TEST_BRANCH_JOB);

        assertEquals("test-bamboo/TEST-MASTER/B/1", subjectIdService.getSubjectId(TEST_JOB_MASTER_KEY));
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
        when(cachedPlanManager.getPlanByKey(TEST_VERY_LONG_PLAN_KEY)).thenReturn(TEST_VERY_LONG_PLAN);
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_ID_LONG_PLAN_KEY))
                .thenReturn(TEST_DEPLOYMENT_LONG_PLAN_KEY);

        assertEquals(
                "test-bamboo/TESTTHISISSUSPICIOUSLYLONG-PLANTOOOOOL/B/1234566789",
                subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY));
        assert (subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY).length() <= IAM_REQUEST_LIMIT);

        assertEquals(
                "test-bamboo/TESTTHISISSUSPICIOUSLYLONG-PLANTOOOOOLONGTOBE/D/987",
                subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID_LONG_PLAN_KEY));
        assert (subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID_LONG_PLAN_KEY).length() <= IAM_REQUEST_LIMIT);
    }

    @Test
    public void testLongInstanceName() {
        when(admConfAccessor.getAdministrationConfiguration().getInstanceName())
                .thenReturn("this-is-a-very-long-instance-name-this-is-way-too-long-who-would-make-a-name-this-long");
        when(cachedPlanManager.getPlanByKey(TEST_VERY_LONG_PLAN_KEY)).thenReturn(TEST_VERY_LONG_PLAN);
        when(deploymentProjectService.getDeploymentProject(TEST_DEPLOYMENT_ID)).thenReturn(TEST_DEPLOYMENT);

        assertEquals(
                "this-is-a-very-long-instance-name-this-is-way-too-long-/D/12345",
                subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID));
        assert (subjectIdService.getSubjectId(TEST_DEPLOYMENT_ID).length() <= IAM_REQUEST_LIMIT);
        assertEquals(
                "this-is-a-very-long-instance-name-this-is-way-too-/B/1234566789",
                subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY));
        assert (subjectIdService.getSubjectId(TEST_VERY_LONG_PLAN_KEY).length() <= IAM_REQUEST_LIMIT);
    }

    private ImmutablePlan mockPlan(PlanKey planKey, long planId) {
        ImmutablePlan plan = mock(ImmutablePlan.class);
        when(plan.getPlanKey()).thenReturn(planKey);
        when(plan.getId()).thenReturn(planId);
        when(plan.getPlanType()).thenReturn(PlanType.CHAIN);
        return plan;
    }

    private ImmutableJob mockJob(PlanKey planKey, long planId) {
        ImmutableJob job = mock(ImmutableJob.class);
        when(job.getPlanType()).thenReturn(PlanType.JOB);

        ImmutableChain parent = mock(ImmutableChain.class);
        when(parent.getPlanKey()).thenReturn(TEST_PARENT_KEY);
        when(parent.getId()).thenReturn(planId);

        when(job.getParent()).thenReturn(parent);
        return job;
    }

    private ImmutableJob mockBranchJob(PlanKey planKey, long planId) {
        ImmutableJob job = mock(ImmutableJob.class);
        when(job.getPlanType()).thenReturn(PlanType.JOB);

        // Use lenient mock so that failure case is more obvious
        ImmutableChain plan = mock(ImmutableChain.class, Mockito.withSettings().lenient());
        when(job.getParent()).thenReturn(plan);
        when(plan.hasMaster()).thenReturn(true);
        // We expect the following mocks do not get called normally, only in a failure scenario
        when(plan.getPlanKey()).thenReturn(TEST_PARENT_KEY);
        when(plan.getId()).thenReturn(3L);

        ImmutableChain master = mock(ImmutableChain.class);
        when(plan.getMaster()).thenReturn(master);
        when(master.getPlanKey()).thenReturn(TEST_MASTER_PARENT_KEY);
        when(master.getId()).thenReturn(planId);

        return job;
    }

    private DeploymentProject mockDeployment(PlanKey planKey, Long deploymentId) {
        DeploymentProject deploymentProject = mock(DeploymentProject.class);
        when(deploymentProject.getId()).thenReturn(deploymentId);
        when(deploymentProject.getPlanKey()).thenReturn(planKey);
        return deploymentProject;
    }
}
