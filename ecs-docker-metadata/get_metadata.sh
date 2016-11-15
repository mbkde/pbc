#!/bin/sh

set -ex

TARGET_DIR=/buildeng/bamboo-agent-home/xml-data/build-dir
mkdir -p $TARGET_DIR
docker ps -aq | xargs docker inspect | jq --arg result_id $RESULT_ID -r '
  . as $containers
  # Find containers with $RESULT_ID set, and has a task-arn label
  # (to skip manually created containers) and get the task arn.
  # We get the first, as multiple containers per task have the envvar set
  | first($containers[] | select((.Config.Env[]? | contains($result_id)) and (.Config.Labels | has("com.amazonaws.ecs.task-arn"))).Config.Labels["com.amazonaws.ecs.task-arn"]) as $current_arn
  # Get all containers within the task (Including sidekick, extra containers, etc.)
  | [ $containers[] | select(.Config.Labels["com.amazonaws.ecs.task-arn"] == $current_arn)]
  # Extract the image hash, image tag and container name
  | map({hash: .Image, tag: .Config.Image, name: .Config.Labels["com.amazonaws.ecs.container-name"]})' > $TARGET_DIR/metadata
cat $TARGET_DIR/metadata
