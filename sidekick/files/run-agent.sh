#!/bin/bash -l
set -x


if [ -f '/buildeng-custom/setup.sh' ]; then
    source /buildeng-custom/setup.sh
fi

# Register as a docker builder
./bamboo-update-capability "system.isolated.docker" $IMAGE_ID
./bamboo-update-capability "system.isolated.docker.for" $RESULT_ID

# create a dockery name for the agent.
cat > bamboo-agent-home/bamboo-agent.cfg.xml <<EOF
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<configuration>
    <buildWorkingDirectory>/buildeng/bamboo-agent-home/xml-data/build-dir</buildWorkingDirectory>
    <agentDefinition>
        <name>Docker agent for $RESULT_ID</name>
        <description>Docker agent $IMAGE_ID for $RESULT_ID</description>
    </agentDefinition>
</configuration>
EOF

# kill the agent after running the given timeout, we don't expect jobs to run longer than this
TIMEOUT=6h

# Actually run the agent
while [ $? -eq 0 ]; do
    timeout --signal=KILL $TIMEOUT jre/bin/java  \
 -Dbamboo.home=bamboo-agent-home -DDISABLE_AGENT_AUTO_CAPABILITY_DETECTION=true -jar atlassian-bamboo-agent.jar $BAMBOO_SERVER/agentServer/
done

exit 0
