ECS scheduler service
==============

A microservice to deal with scheduling bamboo agent tasks onto ECS cluster and scaling the cluster based on current demand.
It is assumed that one such service handles a single ECS cluster/Autoscaling group.

Distribution
====

Binary executable jar: [ecs-scheduler-service-*.jar](https://bitbucket.org/atlassian/per-build-container/downloads/)


Infrastructure
====

The assumed existing infrastructure includes:

* AWS ECS cluster backed by EC2 Autoscaling Group+Launch Configuration. The instances in the ASG are meant to be running
the [ECS optimized AMI](http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-optimized_AMI.html) or equivalent.

* the following AIM permissions
    - __arn:aws:iam::aws:policy/AmazonEC2ContainerServiceFullAccess__ for full access to ECS cluster.
    - this custom policy to manipulate the Autoscaling Group.
```
{
    "Statement": [
        {
            "Action": [
                "autoscaling:SetDesiredCapacity",
                "autoscaling:DetachInstances",
                "autoscaling:EnableMetricsCollection",
                "autoscaling:DisableMetricsCollection",
                "autoscaling:SuspendProcesses"
            ],
            "Effect": "Allow",
            "Resource": "*"
        },
        {
            "Action": "ec2:TerminateInstances",
            "Effect": "Allow",
            "Resource": "arn:aws:ec2:*:*:instance/*"
        }
    ],
    "Version": "2012-10-17"
}
```
    - this optional custom policy to access and send container logs to aws via awslogs driver:
```
{
    "Statement": [
        {
            "Action": [
                "logs:GetLogEvents",
                "logs:CreateLogStream",
                "logs:PutLogEvents",
                "ec2:GetConsoleOutput"
            ],
            "Effect": "Allow",
            "Resource": "*"
        }
    ],
    "Version": "2012-10-17"
}
```

Configuration
====

The service is setup by the following environment variables:

Basic setup:

* ECS_ASG - EC2 autoscaling group name
* ECS_CLUSTER - ECS cluster name
* ECS_TASK_DEF - task definition family name - where the service will create task definition revisions for each unique configuration combination.

Optional:

* ECS_LOGDRIVER - name of the logdriver to send task container logs to. Eg. 'awslogs'
* ECS_LOGOPTIONS - comma separated list of env variable names that the log driver (ECS_LOGDRIVER) will be configured with. Eg. 'awslogs-region,awslogs-group,awslogs-stream-prefix'.
It is expected that the named env variables are also defined.



To enable sending events to Datadog:

* DATADOG_API_KEY - the api key to use when sending content to Datadog
* AWS_REGION - (optional for Docker based distribution if pulling the DATADOG_API_KEY from KMS via unicreds) - region where to use unicreds/KMS.

