#!/bin/bash

set -euf
if [ -n "${BASH_VERSION:+x}" -o -n "${ZSH_VERSION:+x}" ]; then
      set -o pipefail
fi

export AWS_REGION="${AWS_REGION:=us-east-1}"
export DATADOG_API_KEY=$(/service/unicreds -r $AWS_REGION get datadog_api_key | head -c -1)

java $@ -jar /service/EcsSchedulerService.jar
