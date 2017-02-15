# Per Build Containers

## Sidekick image


* Description:
    * This image encapsulates all mandatory content to successfuly run a Bamboo agent
    inside a Docker container and makes it available as volume for use by other containers.
    That way, makes the actual Docker images used free of Bamboo specific content.
    * This is the data volume which mounts a volume at `/buildeng`. The volume is to contain:
        * Script to run the agent at /buildeng/run-agent.sh
        * Untarred jre installation at /buildeng/jre/
        * Bamboo agent jar optionally preloaded with plugins from your Bamboo instance at /buildeng/atlassian-bamboo-agent.jar
        * Pre-initialised `bamboo-capabilities.properties` file. See below for how to add custom capabilities.
        * jq binary at /buildeng/jq
    * Currently the image uses a small base image `tianon/true` which only contains `bin/true`. This saves us having to start an operating system.

## Assumptions/API contracts

  * Environment Variables:
    * `IMAGE_ID` defines which docker image the agent is running, used to set a capability for bamboo
    * `BAMBOO_SERVER` defines the server to connect to, eg. `http://my-bamboo-server.com`
    * `RESULT_ID` defines the job result id that the agent is being executed for.
  * Docker settings
    * For the agent to operate as expected the ENTRYPOINT (`--entrypoint=`) should be `/buildeng/run-agent.sh`
    * The working directory (`-w`) should be `/buildeng`
  * When used via the PBC bamboo plugins all of these are automatically set

## Agent image restrictions

You can effectively use any Linux image as your agent container, though we have some minor restrictions

* Your image must have bash, as we use it in our run script
* glibc needs to be installed for oracle-java8 JRE to run. That's the JRE the agent is currently running with.


## How To create a sidekick for your environment

To generate a sidekick locally:

* clone this repository
* install docker
* `scripts/build_sidekick.sh` takes some parameters like where to get jre, what bamboo agent version to use and optionally what bamboo server to prewarm against.
* the script does not actually push the sidekick docker image but will only create a local one.
```
JAVA_URL=Your internal path to JRE tar.gz.
scripts/build_sidekick.sh -j $JAVA_URL  -b 5.10.1 -B https://staging-bamboo.atlassian.com

```
