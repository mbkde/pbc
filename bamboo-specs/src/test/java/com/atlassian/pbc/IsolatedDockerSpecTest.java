package com.atlassian.pbc;

import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.util.EntityPropertiesBuilders;
import org.junit.Test;

public class IsolatedDockerSpecTest {
    @Test
    public void checkYourPlanOffline() {
        Plan plan = new IsolatedDockerSpec().plan();

        EntityPropertiesBuilders.build(plan);
    }
}
