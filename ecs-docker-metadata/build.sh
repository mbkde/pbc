#!/bin/sh

set -ex

TAG=${1:-development}
IMAGE=docker.atlassian.io/buildeng/ecs-docker-metadata

docker build -t $IMAGE:$TAG .
