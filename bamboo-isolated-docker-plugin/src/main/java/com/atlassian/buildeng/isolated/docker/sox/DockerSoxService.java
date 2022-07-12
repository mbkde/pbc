/*
 * Copyright 2016 - 2017 Atlassian Pty Ltd.
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

package com.atlassian.buildeng.isolated.docker.sox;

import com.atlassian.bamboo.FeatureManager;
import com.atlassian.bamboo.bandana.PlanAwareBandanaContext;
import com.atlassian.bamboo.persister.AuditLogEntry;
import com.atlassian.bamboo.persister.AuditLogMessage;
import com.atlassian.bamboo.persister.AuditLogService;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.buildeng.spi.isolated.docker.Configuration;
import com.atlassian.plugin.spring.scanner.annotation.component.BambooComponent;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BambooComponent
public class DockerSoxService {
    private static final Logger logger = LoggerFactory.getLogger(DockerSoxService.class);

    private final FeatureManager featureManager;
    private final AuditLogService auditLogService;
    private final BandanaManager bandanaManager;
    private final BambooAuthenticationContext authenticationContext;
    static final String BANDANA_SOX_ENABLED = "com.atlassian.buildeng.pbc.sox.enabled";
    static final String BANDANA_SOX_PATTERNS = "com.atlassian.buildeng.pbc.sox.whitelist";
    private List<Pattern> soxPatterns;

    @Inject
    public DockerSoxService(FeatureManager featureManager, AuditLogService auditLogService,
            BandanaManager bandanaManager, BambooAuthenticationContext authenticationContext) {
        this.featureManager = featureManager;
        this.auditLogService = auditLogService;
        this.bandanaManager = bandanaManager;
        this.authenticationContext = authenticationContext;
    }


    public boolean isSoxEnabled() {
        return getConfig().isEnabled(); //featureManager.isSoxComplianceModeConfigurable()
    }
    

    public boolean checkSoxCompliance(Configuration config) {
        if (isSoxEnabled()) {
            Stream<String> images = Stream.concat(
                    Stream.of(config.getDockerImage()),
                    config.getExtraContainers().stream().map((Configuration.ExtraContainer t) -> t.getImage()));
            return images.allMatch(matchesPatterns());
        }
        return true;
    }

    private Predicate<String> matchesPatterns() {
        return (String image) -> getSoxPatterns()
                .stream().filter((Pattern t) -> t.matcher(image).matches()).findFirst().isPresent();
    }

    public synchronized void updateConfig(SoxRestConfig config) {
        validatePatterns(config.getWhitelistPatterns());

        SoxRestConfig old = getConfig();
        if (old.isEnabled() != config.isEnabled()) {
            AuditLogEntry ent = new  AuditLogMessage(this.authenticationContext.getUserName(),
                    new Date(), null, null, null, null,
                    AuditLogEntry.TYPE_FIELD_CHANGE, "PBC SOX Enabled",
                    Boolean.toString(old.isEnabled()), Boolean.toString(config.isEnabled()));
            auditLogService.log(ent);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SOX_ENABLED, config.isEnabled());
        }
        if (!Arrays.equals(old.getWhitelistPatterns(), config.getWhitelistPatterns())) {
            AuditLogEntry ent = new  AuditLogMessage(this.authenticationContext.getUserName(),
                    new Date(), null, null, null, null,
                    AuditLogEntry.TYPE_FIELD_CHANGE, "PBC SOX Whitelist",
                    Arrays.toString(old.getWhitelistPatterns()), Arrays.toString(config.getWhitelistPatterns()));
            auditLogService.log(ent);
            bandanaManager.setValue(PlanAwareBandanaContext.GLOBAL_CONTEXT, BANDANA_SOX_PATTERNS,
                    config.getWhitelistPatterns());
            soxPatterns = null;
        }
    }

    public synchronized SoxRestConfig getConfig() {
        Boolean enabled = (Boolean) bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_SOX_ENABLED);
        enabled = enabled != null ? enabled : Boolean.FALSE;
        String[] whitelist = (String[])bandanaManager.getValue(PlanAwareBandanaContext.GLOBAL_CONTEXT,
                BANDANA_SOX_PATTERNS);
        whitelist = whitelist != null ? whitelist : new String[0];
        return new SoxRestConfig(enabled, whitelist);
    }

    private synchronized List<Pattern> getSoxPatterns() {
        if (soxPatterns == null) {
            SoxRestConfig config = getConfig();
            String[] patts = config.getWhitelistPatterns();
            if (patts != null) {
                soxPatterns = Arrays.asList(patts).stream().map((String t) -> {
                    try {
                        return Pattern.compile(t);
                    } catch (PatternSyntaxException ex) {
                        logger.error("Cannot compile SOX whitelist pattern for - {}", t);
                        return Pattern.compile("X");
                    }
                }).collect(Collectors.toList());
            } else {
                soxPatterns = Collections.emptyList();
            }
        }
        return soxPatterns;
    }

    private void validatePatterns(String[] whitelistPatterns) throws WebApplicationException {
        if (whitelistPatterns != null) {
            for (String patt : whitelistPatterns) {
                try {
                    Pattern.compile(patt);
                } catch (PatternSyntaxException e) {
                    logger.error("Cannot validate SOX whitelist pattern for - {}", patt);
                    throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
                }
            }
        }
    }
    
}
