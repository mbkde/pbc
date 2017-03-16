Bamboo Agent capabilites
========================

In general the capabilities-requirement system in Bamboo is not highly relevant for PBC agents. The job is guaranteed to run on
the agent it designated, so the process of matching requirements of jobs to capabilities of existing agents is obsolete.
However there are a lot of tool specific Bamboo tasks that require capabilities to be present on agents in order to work.
Eg. Maven, Ant, Node.js tasks.

Capabilities are specific to the actual agent docker image. The sidekick only provides the utility script `/buildeng/bamboo-update-capability` to register capabilities at startup.

To Register capabilities in agent Docker image:

* create `setup.sh` file
* for each capability add a line like
```
# JDK capability
/buildeng/bamboo-update-capability "system.jdk.JDK 1.8" "/usr/lib/jvm/java-8-openjdk-amd64"
#Python capability
/buildeng/bamboo-update-capability "system.builder.command.Python" "/usr/local/bin/python"
# custom capability
/buildeng/bamboo-update-capability "os" "Linux"

```

* add the file to your Docker image at path `/buildeng-custom/setup.sh`
