Gotchas and known limitations
=============================

* Simple docker backends only implements a subset of features. It might be missing
Extra containers, container resource reservations, bits to have Docker in Docker work etc.

* The ECS backend can currently only have the following EC2 instance types in the AutoScaling Group:
m4.xlarge, m4.4xlarge, m4.10xlarge and m4.16xlarge. It should be simple to extend in [ECSInstance.java](ecs-scheduler/src/main/java/com/atlassian/buildeng/ecs/scheduling/ECSInstance.java)

* All containers in ECS task need to fit on single EC2 instance in ASG. The UI will allow to define larger container sizes
but the such agents will not be able to materialize.

* We've only tested with close to unlimited license in terms of number of agents available. 
[Issue 13](https://bitbucket.org/atlassian/per-build-container/issues/13/bamboo-pbc-build-fails-when-license-limit) added some limit handling but might not be bulletproof.

* Our Bamboo instance once in a while end up in trouble when they try to start 500+ agent at about the same time.

* When attempting to run Docker in Docker, we start any docker:*dind named containers with privileged mode turned on.
The inner Docker daemon needs to synchronize storagedriver with the outer Docker daemon.
 We add --storage-driver=overlay2 by default. You can override that with a Java system property of `pbc.dind.storage.driver`
passed to either Bamboo server JVM or the ecs-scheduler-service (if using the Remote ECS backend).

* ECS backends configured with awslogs log driver will show links to all the containers in the build result UI.

* We've had bad experience with fluentd log driver in ECS that managed to crash Docker daemon occasionally.

* We've had bad experience with the default storage driver on AWS ECS AMI and changed it to overlay and then overlay2.

* Kubernetes scaling and scheduling of pods is outside of the scope of the plugin. You have to deal with it on Kube side.

* Kubernetes container logs storage is also out of scope.



