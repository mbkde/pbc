package com.atlassian.buildeng.kubernetes.condition;

import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;
import java.util.Map;

public class LoggedInUser implements Condition
{
    private final BambooAuthenticationContext authenticationContext;

    public LoggedInUser(BambooAuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void init(Map<String, String> params) throws PluginParseException
    {

    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context)
    {
        return authenticationContext.getUserName() != null;
    }
}
