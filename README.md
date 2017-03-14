Project Name
==============

Set of plugins for [Atlassian Bamboo](https://www.atlassian.com/software/bamboo). Allows running builds and deployments
on Bamboo agents in Docker clusters like Docker Swarm, [Kubernetes](https://kubernetes.io/) and [AWS ECS](https://aws.amazon.com/ecs/). Tools and services for the build defined
as Docker images.

Each execution of a Bamboo job or deployment environment will run on a newly created remote agent
that will contain exactly the tools needed for the job, run it and then destroy itself. Services as Selenium, databases or Docker
can be defined as extra containers that the build can interact with.

What is it good for?
====================

* Teams own their build pipelines, including the definition of build agents
* Teams upgrade their dependencies on tools and services individually, at their timetable
* Builds in CI (Bamboo) match the local dev environment in terms of tools used or can be at least easily reproduced locally with ease
* Support a wide variety of tool combinations on a Bamboo instance with limited agent number license.


Usage
======

You would typically install a subset of Bamboo plugins depending on what infrastructure backend you going to use.
The most battle hardened are the ones backed by AWS ECS. Download all binaries in the [Download](https://bitbucket.org/atlassian/per-build-container/downloads/) section.

* [bamboo-isolated-docker-plugin](bamboo-isolated-docker-plugin/README.md) - the general UI and bamboo lifecycle management. Mandatory plugin.
* [isolated-docker-spi](isolated-docker-spi/README.md) - the plugin with API for the various backends. Mandatory plugin.
* [bamboo-isolated-ecs-plugin](bamboo-isolated-ecs-plugin/README.md) - AWS ECS backed plugin that performs the scheduling and scaling of ECS cluster from Bamboo server. __Implies one ECS cluster per Bamboo Server__.
* [bamboo-remote-ecs-backend-plugin](bamboo-remote-ecs-backend-plugin/README.md) - Backend talking to a remote service that talks to ECS. Allows multiple Bamboo servers scheduling on single ECS cluster. This
requires you to setup a separate service and infrastructure, see [Setup pbc-scheduler microservice](ecs-scheduler-service/README.md)
* bamboo-simple-backend-plugin - Experimental backend that runs the Docker agents directly on the Bamboo server or a single remote instance.
* bamboo-kubernetes-backend-plugin - Experimental backend that schedules agents on Kubernetes cluster.

In any of these cases you will have to configure some global settings in the Bamboo's Administration section. Eg. point to the ECS cluster to use. See individual plugin's documentation for details.

Important:
==========
You will also require a 'sidekick' image. That's a Docker image with just a volume defined containing JRE + agent jars tailored for your Bamboo instance.
`mkleint/sidekick-openjdk` is minimal version for experimenting purposes only.
Please build the image yourself from sources and push it to your docker registry. Check [sidekick README](sidekick/README.md) for details


Installation
============

* First and foremost, you need an existing Bamboo installation.
* Then you need to decide what Docker clustering solution to use (where your builds will be running).
We recommend AWS ECS right now as it's the most (the only one) battle-hardened. See [ECS infrastructure requirements](ecs-scheduler-service/README.md)
* Generate a [sidekick](sidekick/README.md) Docker image and push to your Docker registry.
* Then install the appropriate Bamboo plugins and configure them. Follow the links in the __Usage__ section to learn how to setup each plugin.
* Create a bamboo plan with a simple echo script task job, configure it to be run on `ubuntu:16.04` in [Job's Miscellaneous tab](bamboo-isolated-docker-plugin/README.md) and run the build!
* If everything is setup correctly, a new bamboo agent will start up shortly and build your plan.


Documentation
=============

* [ECS And PBC microservice](ecs-scheduler-service/README.md) setup. Also includes infrastructure requirements that are the same for [bamboo-isolated-ecs-plugin](bamboo-isolated-ecs-plugin/README.md) as well.
* creating Docker images for builds with [Bamboo agent capabilities](sidekick/capabilities.md)
* [secrets management considerations](sidekick/secrets.md)

Tests
=====


Contributors
============

Pull requests, issues and comments welcome. For pull requests:

* Add tests for new features and bug fixes
* Follow the existing style
* Separate unrelated changes into multiple pull requests

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

* [CLA for corporate contributors](https://na2.docusign.net/Member/PowerFormSigning.aspx?PowerFormId=e1c17c66-ca4d-4aab-a953-2c231af4a20b)
* [CLA for individuals](https://na2.docusign.net/Member/PowerFormSigning.aspx?PowerFormId=3f94fbdc-2fbe-46ac-b14c-5d152700ae5d)

License
========

Copyright (c) 2016 - 2017 Atlassian and others.
Apache 2.0 licensed, see [LICENSE.txt](LICENSE.txt) file.
