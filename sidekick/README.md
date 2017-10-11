# Per Build Containers

A mandatory Docker image with Bamboo specific bits volume. The image used is to be configured in Administration section for relevant Bamboo plugins.
`mkleint/sidekick-openjdk` is minimal version for experimenting purposes only.


## Sidekick image content

* This image encapsulates all mandatory content to successfully run a Bamboo agent
inside a Docker container and makes it available as volume for use by other containers.
That way, makes the actual Docker images used free of Bamboo specific content.
* This is the data volume which mounts a volume at `/buildeng`. The volume is to contain:
    * Script to run the agent at /buildeng/run-agent.sh
    * jre installation at /buildeng/jre/
    * Bamboo agent jar optionally preloaded with plugins from your Bamboo instance at /buildeng/atlassian-bamboo-agent.jar
    * Script to add capabilities to the agent at runtime. See below for how to add custom capabilities.
    * jq binary at /buildeng/jq
* Currently the image uses a small base image `tianon/true` which only contains `bin/true`. This saves us having to start an operating system.

## Assumptions/API contracts

  * Environment Variables:
    * `IMAGE_ID` defines which docker image the agent is running, used to set a capability for bamboo
    * `BAMBOO_SERVER` defines the server to connect to, eg. `http://my-bamboo-server.com`
    * `RESULT_ID` defines the job result id that the agent is being executed for.
  * The agent docker images can store custom initialization in `/buildeng-custom/setup.sh`. The script will be called from sidekick startup script before the bamboo agent starts.
Typically used to setup agent capabilities and secrets.

## Agent image restrictions

You can effectively use any Linux image as your agent container, though we have some minor restrictions

* Your image must have bash, as we use it in our run script
* glibc needs to be installed for oracle-java8 JRE to run. That's the JRE the agent is currently running with.
That limits usage of alpine based images.

## Capabilities

See [capabilities](capabilities.md) docs on how to add capabilities to PBC agent images. The sidekick image provides
just the tools (`buildeng/bamboo-update-capability`) only.


## How To create a sidekick for your environment

To generate a sidekick locally:

* install docker
* clone this repository, `cd sidekick`
* download Oracle [JRE](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html) and place it inside the working directory. Optionally if you have a company wide distribution url, use that one at a later step.
* run `scripts/build_sidekick.sh` that takes some parameters like where to get jre from, what bamboo agent version to use and optionally what bamboo server to prewarm against.
* the script does not actually push the sidekick docker image but will only create a local one named `bamboo-agent-sidekick:development`

```
JAVA_PATH=<Path to JRE inside working directory or URL for download>
BAMBOO_VERSION=5.10.1
BAMBOO_SERVER=<Your Bamboo server URL>
BAMBOO_USER=<Your Bamboo admin password>
BAMBOO_PASSWORD=<Your Bamboo admin password>
scripts/build_sidekick.sh -j $JAVA_PATH  -b $BAMBOO_VERSION -B $BAMBOO_SERVER

```
When using -B switch to pre-warm the bamboo plugin caches, the server needs to have remote agents enabled.
