# Per-build Container (PBC) Bamboo Agents

Set of plugins for [Atlassian Bamboo](https://www.atlassian.com/software/bamboo). Allows running builds and deployments
on Bamboo agents in Docker clusters like Docker Swarm, [Kubernetes](https://kubernetes.io/)
and [AWS ECS](https://aws.amazon.com/ecs/). Tools and services for the build defined
as Docker images.

Each execution of a Bamboo job or deployment environment will run on a newly created remote agent
that will contain exactly the tools needed for the job, run it and then destroy itself. Services as Selenium, databases
or Docker
can be defined as extra containers that the build can interact with.

# Bamboo Compatibility Note

- Version 1.x (currently latest is 1.228) was compiled against Bamboo 6.2.0 and should work in any version above that as
  well.
- Version 2.x (current master branch) only works in 6.6.0 and later. Everyone is encouraged to upgrade to this version.
  It it's using a different UI for configuration of the feature. Instead of Miscellaneous tab for Jobs and task for
  Deployments,
  it's using the new Docker tab in both.

# What is it good for?

- Teams own their build pipelines, including the definition of build agents
- Teams upgrade their dependencies on tools and services individually, at their timetable
- Builds in CI (Bamboo) match the local dev environment in terms of tools used or can be at least easily reproduced
  locally with ease
- Support a wide variety of tool combinations on a Bamboo instance with limited agent number license.

# Usage

You would typically install a subset of Bamboo plugins depending on what infrastructure backend you are going to use.
The most battle hardened is the one backed by Kubernetes. Other options (such as ECS) are currently unmaintained.
Download all binaries in
the [Download](https://bitbucket.org/atlassian/per-build-container/downloads/) section.

These two have to be always installed:

- [bamboo-isolated-docker-plugin](bamboo-isolated-docker-plugin/README.md) - the general UI and bamboo lifecycle
  management. Mandatory plugin.
- [isolated-docker-spi](isolated-docker-spi/README.md) - the plugin with API for the various backends. Mandatory plugin.

You will also need to install one of these based on what is your preferred containerization backend:

- [bamboo-kubernetes-backend-plugin](bamboo-kubernetes-backend-plugin/README.md) - Backend that schedules agents on
  Kubernetes cluster. The plugin only starts pods on the cluster, scaling is to be done by kube native components.
- [bamboo-isolated-ecs-plugin](bamboo-isolated-ecs-plugin/README.md) - AWS ECS backed plugin that performs the
  scheduling and scaling of ECS cluster from Bamboo server. **Implies one ECS cluster per Bamboo Server**.
- [bamboo-remote-ecs-backend-plugin](bamboo-remote-ecs-backend-plugin/README.md) - Backend talking to a remote service
  that talks to ECS. Allows multiple Bamboo servers scheduling on single ECS cluster. This
  requires you to setup a separate service and infrastructure,
  see [Setup pbc-scheduler microservice](ecs-scheduler-service/README.md)
- bamboo-simple-backend-plugin - Experimental backend that runs the Docker agents directly on the Bamboo server or a
  single remote instance.

In any of these cases you will have to configure some global settings in the Bamboo's Administration section. Eg. point
to the ECS cluster to use. See individual plugin's documentation for details.

# Important:

You will also require a 'sidekick' image. That's a Docker image with just a volume defined containing JRE + agent jars
tailored for your Bamboo instance.
`mkleint/sidekick-openjdk` is minimal version for experimenting purposes only.
Please build the image yourself from sources and push it to your docker registry.
Check [sidekick README](sidekick/README.md) for details

# Installation

- First and foremost, you need an existing Bamboo installation.
- IMPORTANT: Enable remote agents and make sure 'Remote agent authentication is disabled'. When using PBC, each build
  will register a new remote agent
  and that agent needs to be allowed to pick up the job without manual agent approvals.
- Then you need to decide what Docker clustering solution to use (where your builds will be running).
  We recommend AWS ECS or Kubernetes right now as these are the most battle-hardened.
  See [ECS infrastructure requirements](ecs-scheduler-service/README.md)
- Generate a [sidekick](sidekick/README.md) Docker image and push to your Docker registry.
- Then install the appropriate Bamboo plugins and configure them. Follow the links in the **Usage** section to learn how
  to setup each plugin.
- Create a bamboo plan with a simple echo script task job, configure it to be run on `ubuntu:16.04`
  in [Job's Miscellaneous tab](bamboo-isolated-docker-plugin/README.md) and run the build!
- If everything is setup correctly, a new bamboo agent will start up shortly and build your plan.

# Documentation

- [ECS And PBC microservice](ecs-scheduler-service/README.md) setup. Also includes infrastructure requirements that are
  the same for [bamboo-isolated-ecs-plugin](bamboo-isolated-ecs-plugin/README.md) as well.
- Creating Docker images for builds with [Bamboo agent capabilities](sidekick/capabilities.md)
- [Secrets management considerations](sidekick/secrets.md)
- [Gotchas and known limitations](knownlimitations.md)
- [Differences between agents on ECS and Kube](extra-containers.md)

# Bamboo Specs

YAML Bamboo Specs configuration is supported since Bamboo 6.10.0 and plugin v2.42+

Short format

```yaml
version: 2
---
job:
  other:
    pbc: mkleint/sidekick-openjdk
```

Long format

```yaml
version: 2
---
job:
  other:
    pbc:
      image: mkleint/sidekick-openjdk
      size: REGULAR # XXLARGE, XLARGE, LARGE, SMALL, XSMALL
      awsRole: arn:aws:iam::123:role/igor
      extra-containers:
        - name: test
          image: mkleint/sidekick-openjdk-extra
          size: REGULAR # XXLARGE, XLARGE, LARGE, SMALL
          commands:
            - echo hi
            - touch /etc/info
          variables:
            var1: value1
            var2: value2
```

# Contributors

Pull requests, issues and comments welcome. For pull requests:

- Add tests for new features and bug fixes
- Follow the existing style
- Separate unrelated changes into multiple pull requests

See the existing issues for things to start contributing.

For bigger changes, make sure you start a discussion first by creating
an issue and explaining the intended change.

Atlassian requires contributors to sign a Contributor License Agreement,
known as a CLA. This serves as a record stating that the contributor is
entitled to contribute the code/documentation/translation to the project
and is willing to have it used in distributions and derivative works
(or is willing to transfer ownership).

Prior to accepting your contributions we ask that you please follow the appropriate
link below to digitally sign the CLA. The Corporate CLA is for those who are
contributing as a member of an organization and the individual CLA is for
those contributing as an individual.

- [CLA for corporate contributors](https://na2.docusign.net/Member/PowerFormSigning.aspx?PowerFormId=e1c17c66-ca4d-4aab-a953-2c231af4a20b)
- [CLA for individuals](https://na2.docusign.net/Member/PowerFormSigning.aspx?PowerFormId=3f94fbdc-2fbe-46ac-b14c-5d152700ae5d)

# License

Copyright (c) 2016 - 2018 Atlassian and others.
Apache 2.0 licensed, see [LICENSE.txt](LICENSE.txt) file.

# Local Development

Use the [Atlassian plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/) to develop this plugin.
To download this:

```bash
brew tap atlassian/tap
brew install atlassian/tap/atlassian-plugin-sdk
```

**(Terminal 1)** Then, `cd` into the directory of the plugin you are testing, e.g. `cd bamboo-isolated-docker-plugin`
and run:

```bash
atlas-debug
```

**(Terminal 2)** If you make any changes to the code simply run

```bash
mvn package
```

from the root of the directory. In cases where you are updating a JavaScript file you may need to perform a
hard refresh in the browser for the changes to be reflected. You could also disable the cache for
the page in DevTools.

Note: you will need to specify which mvn to use: `export ATLAS_MVN=$(which mvn)`.
It may be handy to add this to your bash profile: `echo export ATLAS_MVN=$(which mvn) >> ~/.zshrc`
so that you do not need to repeat this step each time.
This is only a workaround until [ATLASSDK-197](https://ecosystem.atlassian.net/browse/ATLASSDK-197) is implemented

## CodeStyle

We use IntelliJ codestyle formatting for this repository. The codestyle format
used is defined in the `.idea/Codestyle/project.xml` file. You can check that
this is being used correctly by looking under your settings for
`Settings > Editor > Code Style > Java` and the `Scheme` should have `Project` selected.

We also recommend using format on save to ensure you are always applying this
Code Style. This can also be enabled in the IntelliJ settings with
`Settings > Tools > Action on Save` and ticking `Reformat Code` and
`Optimize Imports`

## CheckStyle

As part of the pipeline associated with this repo a checkstyle test is run.
In order to be able to run this locally simply:

1. Open [IntelliJ IDEA → Preferences → Plugins tab → Marketplace → search CheckStyle-IDEA]
2. Install the one which matches the name completely
3. Restart IntelliJ IDEA
   Then, simply scan the file you would like tested using the `Build Engineering Checks` rule.
   If you get an error like `The scan failed due to an error - please see the event log for more information` it is most
   likely do to an incompatibility between our checkstyle rules and the checkstyle version. See the checkstyle version
   set in the root pom.xml and then in IntelliJ go to `Preferences > Tools > Checkstyle` and set it to that version.
