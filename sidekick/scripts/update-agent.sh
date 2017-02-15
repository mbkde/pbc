#!/bin/sh
set -eufx
if [ -n "${BASH_VERSION:+x}" -o -n "${ZSH_VERSION:+x}" ]; then
  set -o pipefail
fi

# prewarms the bamboo agent
agent_home=$1
bamboo_server=$2

#bootstrap. first time round it always exits fast
java -Dbamboo.home=${agent_home} -jar output/volume/atlassian-bamboo-agent.jar "${bamboo_server}/agentServer" > output/bootstrap_phase1.log

set +e
java -Dbamboo.home=${agent_home} -jar output/volume/atlassian-bamboo-agent.jar "${bamboo_server}/agentServer" | tee output/bootstrap_phase2.log | while read LOGLINE
do
  if echo "${LOGLINE}" | grep -q 'ready to receive builds'; then
    pkill -P $$ java
  fi
done
set -e

# I am not a number! (Nuke the agent config)
if [ ! -z "${BAMBOO_USER}" ]; then
    agent_id=`cat "${agent_home}/bamboo-agent.cfg.xml" | grep -oPm1 "(?<=<id>)[^<]+"`
    curl -v -u "${BAMBOO_USER}:${BAMBOO_PASSWORD}" "${bamboo_server}/admin/agent/removeAgent.action?agentId=${agent_id}"
fi

rm "${agent_home}/bamboo-agent.cfg.xml"

#these can be recreated on restart, should not take up space on sidekick
rm -rf "${agent_home}/caches/"
rm -rf "${agent_home}/logs/"
