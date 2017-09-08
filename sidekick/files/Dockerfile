#Smallest runnable image (needs to be executed by ECS in a task and kubernetes copies files explicity so tianon/true won't do
FROM alpine:latest

ADD output/volume/ /buildeng/
ADD output/bamboo-agent-home /buildeng/bamboo-agent-home

VOLUME /buildeng/
