#!/bin/bash
set -e
export BAMBOO_USER="${bamboo.plan.template.rollout.bot.username}"
export BAMBOO_PASSWORD="${bamboo.plan.template.rollout.bot.password}"

#use whatever version of bamboo is running on staging
bamboo_version=`curl -u "${BAMBOO_USER}:${BAMBOO_PASSWORD}" --header "Accept:application/json" https://staging-bamboo.internal.atlassian.com/rest/api/latest/info | jq -r ".version"`
set -x
mvn -B -Dbamboo.version=$bamboo_version -Dbamboo.data.version=$bamboo_version  verify
