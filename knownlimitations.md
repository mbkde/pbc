Gotchas and known limitations
=============================

* Kubernetes and simple docker backends only implement a subset of features. They might be missing
Extra containers, container resource reservations, bits to have Docker in Docker work etc.

* The ECS backend can currently only have the following EC2 instance types in the AutoScaling Group:
m4.xlarge, m4.4xlarge, m4.10xlarge and m4.16xlarge. It should be simple to extend in [ECSInstance.java](ecs-scheduler/src/main/java/com/atlassian/buildeng/ecs/scheduling/ECSInstance.java)

* All containers in ECS task need to fit on single EC2 instance in ASG. The UI will allow to define larger container sizes
but the such agents will not be able to materialize.

* We've only tested with close to unlimited license in terms of number of agents available. Unclear what happens when you
run out of license count.

* Our Bamboo instance once in a while end up in trouble when they try to start 500+ agent at about the same time.

* When attempting to run Docker in Docker, we start any docker:*-dind named containers with privileged mode turned on.
The inner Docker daemon needs to synchronize storagedriver with the outer Docker daemon.
 We add --storage-driver=overlay by default. You can override that with a Java system property of `pbc.dind.storage.driver`
passed to either Bamboo server or the ecs-scheduler-service (based on what ECS backend you are using).

* ECS backends configured with awslogs log driver will show links to all the containers in the build result UI.

* We've had bad experience with fluentd log driver that managed to crash Docker daemon occasionally.

* We've had bad experience with the default storage driver on AWS ECS AMI and changed it to overlay.



