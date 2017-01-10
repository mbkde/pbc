#!/bin/bash

#set -eux do not leak api key
#set -o pipefail

export AWS_REGION="${AWS_REGION:=us-east-1}"
export DATADOG_API_KEY=$(./unicreds -r $AWS_REGION get datadog_api_key | head -c -1)

java -jar /service/EcsSchedulerService.jar
