/*
 * Copyright 2022 Atlassian Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.com.atlassian.buildeng.kubernetes;

import com.atlassian.bamboo.pageobjects.BambooTestedProduct;
import com.atlassian.bamboo.pageobjects.BambooTestedProductFactory;
import com.atlassian.bamboo.testutils.backdoor.Backdoor;
import com.atlassian.bamboo.testutils.config.BambooEnvironmentData;
import com.atlassian.bamboo.webdriver.PageObjectInjectionTestRule;
import com.atlassian.bamboo.webdriver.TestInjectionTestRule;
import com.atlassian.bamboo.webdriver.WebDriverTestEnvironmentData;
import com.atlassian.pageobjects.elements.timeout.Timeouts;
import com.atlassian.webdriver.testing.rule.WebDriverScreenshotRule;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public abstract class AbstractPbcTest {
    protected static final BambooEnvironmentData ENVIRONMENT_DATA = new WebDriverTestEnvironmentData();
    protected static final BambooTestedProduct bamboo = BambooTestedProductFactory.create();

    protected final Backdoor backdoor = new Backdoor(ENVIRONMENT_DATA);
    private final WebDriverScreenshotRule screenshotRule = new WebDriverScreenshotRule();
    private final TestInjectionTestRule injectionRule = new TestInjectionTestRule(this, bamboo);
    private final PageObjectInjectionTestRule pageObjectInjectionRule = new PageObjectInjectionTestRule(this, bamboo);

    @Inject
    protected Timeouts timeouts;

    @Rule
    public final TestRule ruleChain =
            RuleChain.outerRule(screenshotRule).around(pageObjectInjectionRule).around(injectionRule);
}
