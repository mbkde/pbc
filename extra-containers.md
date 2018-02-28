PBC Extra containers and differences between implementations
==============

The Extra/Side containers are containers for services run along side the main agent container. Examples include
database, selenium, docker-in-docker or any other service required/expected to be present by the build.
The agent container can reference these services at runtime with the hostname equal to the name of the side container.
Eg. for a container named 'selenium', your selenium server would be present at 'http://selenium:4567'.

Please note that no effort it made on the agent container to start the build only when side containers are ready. 
If the service initialization takes significant amount of time (I'm looking at you Oracle Database), it's the responsibility
of the build job to wait for side container ready state.

AWS ECS backend
===============
ECS creates links between containers using an older Docker run parameter --link. That's one directional link only. 
So the bamboo agent container sees 'selenium' container under this hostname, but the selenium container can't 
reference the agent container in the same way. (so you can't enter the URL for the webapp running in agent container into the 
selenium browser). The workaround in this case is to find out the public IP address of the agent container and pass that 
around.


Kubernetes backend
==================
Kubernetes binds all container ports to 'localhost'. All containers can see each other, but only one can bind to given port number.
For compatibility with other backends, we are inserting symbolic hostnames matching container names, but these also 
point to 127.0.0.1. This setup has mostly only consequences for docker-in-docker scenarios where inner daemon containers
can't easily access containers on the outside (agent container or other side containers)


Simple Docker backend
=====================
Uses docker user network, all containers can see each other under the hostnames derived from container names.