What is this?
=====

This is an Atlassian Bamboo plugin that provides the API for various Docker clustering solutions in per-build container set of plugins. See the parent [README.md](../README.md) for general description of the functionality.

Usage
=====
This plugin needs to be installed but is only relevant if you want to implement additional Docker backend for PBC.

Every such plugin would typically implement
[IsolatedAgentService](src/main/java/com/atlassian/buildeng/spi/isolated/docker/IsolatedAgentService.java) and provide it as component in
`atlassian-plugin.xml`, eg.

```
    <component key="isolatedAgentService" class="com.atlassian.buildeng.ecs.ECSIsolatedAgentServiceImpl" public="true">
        <interface>com.atlassian.buildeng.spi.isolated.docker.IsolatedAgentService</interface>
        <interface>com.atlassian.sal.api.lifecycle.LifecycleAware</interface>
    </component>
```

Please note the `public="true"` part that is required to see such component from other plugins.

