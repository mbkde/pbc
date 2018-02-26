#!/bin/bash -l
set -x

if [ -f '/var/run/secrets/kubernetes.io/serviceaccount/namespace' ]; then
    retries=0
    # 30 retries per minute times 20 minutes = loop 600 times
    # it's so high because of docker downloads of side containers.
    container_start_retry_count=600
    echo "Waiting for $KUBE_NUM_EXTRA_CONTAINERS side containers to start"
    containers_directory=/pbc/kube
    while true; do
        if [ $retries -eq $container_start_retry_count ]; then
            echo "Side containers failed to create file(s) in $containers_directory"
            ls -la $containers_directory
            break
        elif [ "$(find $containers_directory -type f| wc -l)" != "$KUBE_NUM_EXTRA_CONTAINERS" ]; then
            echo "No match, waiting some more"
            sleep 2
            let retries=retries+1
        else 
            echo "all sidecontainers have started"
            break
        fi
    done
fi


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
# To support alpine, we need the '-t' parameter before the timeout
case "$(timeout 2>&1)" in
    *BusyBox*) timeout_flag='-t';;
    *) timeout_flag='';;
esac

# Actually run the agent
while [ $? -eq 0 ]; do
    timeout -s KILL $timeout_flag $TIMEOUT jre/bin/java  \
 -Dbamboo.home=bamboo-agent-home -DDISABLE_AGENT_AUTO_CAPABILITY_DETECTION=true -jar atlassian-bamboo-agent.jar $BAMBOO_SERVER/agentServer/
done

exit 0
