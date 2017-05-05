# Per Build Containers

## Performance metrics Docker image for ECS

Docker container collecting the CPU/memory performance metrics for the entire EC2 instance in rrd database.
The collected rrd files are later used by the [bamboo-ecs-metrics-plugin](../bamboo-ecs-metrics-plugin) to show graphs in
job result tabs.

## How to use the image?

On ECS EC2 instance startup (cloud-init), add

```

export ec2host=$(ifconfig | grep -E "([0-9]{1,3}\.){3}[0-9]{1,3}" | grep -v 127.0.0.1 | awk '{ print $2 }' | cut -f2 -d: | head -n1)
docker -d run  --add-host ec2host:${ec2host} -v /cgroup/:/host/cgroup/ -v /var/lib/docker/buildeng-metrics:/buildeng-metrics mkleint/pbc-ecs-performance-metrics

# 32 of default + 96 for our metrics collector process should be enough
echo "ECS_RESERVED_MEMORY=128" >> /etc/ecs/ecs.config
```




