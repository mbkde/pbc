#!/bin/bash -l

set -ef
if [ -n "${BASH_VERSION:+x}" ] || [ -n "${ZSH_VERSION:+x}" ]; then
    set -o pipefail
fi

# Run inside docker if not already
source scripts/bootstrap.sh
bootstrap $@

# Initialise variables
stage=''
plugin=''
target=''
username=''
password=''
groupId="com.atlassian.buildeng"
alreadyBuilt="false"

set -u

# Manage CLI options
usage() {
    >&2 cat <<USAGE
Usage: $0 [OPTION]...
Build and deploy PBC.

  -s,    Stage [ build | deploy ]
  -p,    Plugin - Which plugin or group of plugins to deploy [ isolated-docker-spi | bamboo-remote-ecs-backend-plugin | bamboo-isolated-docker-plugin | bamboo-ecs-metrics-plugin | all-remote | all-local ]
  -t,    Target - Address of Bamboo server to deploy to.
  -u,    Username - Bamboo username to use
  -w,    Password - Bamboo password to use

USAGE
}

# Check that a variable is set, if not error out with usage info
checkVarSet() {
    var=$1
    value=$2
    if [ -z "$value" ]; then
        >&2 echo "Missing $var argument"
        usage
        exit 1
    fi
}

# Build all the modules from root dir
build() {
    # We only need to build once per script invocation
    if [ "$alreadyBuilt" = "false" ]; then
        mvn -B clean verify javadoc:jar
        alreadyBuilt=true
    fi
}

# Deploy a plugin to bamboo server
deploy() {
    checkVarSet "plugin" "$plugin"
    checkVarSet "username" "$username"
    checkVarSet "password" "$password"
    checkVarSet "target" "$target"

    if [ -d "$plugin" ]; then
        build
        cd $plugin
        mvn install
        mvn -Datlassian.pdk.server.url=$target \
            -Datlassian.pdk.server.username=$username \
            -Datlassian.pdk.server.password=$password \
            -Datlassian.plugin.key=$groupId-$plugin \
            -DskipTests=true \
            com.atlassian.maven.plugins:atlassian-pdk:2.3.2:install
        cd ..
    elif [ "$plugin" = "all-local" ]; then
        for x in isolated-docker-spi bamboo-isolated-docker-plugin bamboo-isolated-ecs-plugin bamboo-ecs-metrics-plugin; do
            plugin=$x
            deploy
        done
    elif [[ "$plugin" = "all-remote" ]]; then
        for x in isolated-docker-spi bamboo-isolated-docker-plugin bamboo-remote-ecs-backend-plugin bamboo-ecs-metrics-plugin; do
            plugin=$x
            deploy
        done
    else
        >&2 echo "Unknown plugin: -$plugin"
        exit 1
    fi
}

# 'Main' starts here
while getopts ':hp:s:t:u:w:' option; do
    case $option in
        s  ) stage=$OPTARG;;
        t  ) target=$OPTARG;;
        p  ) plugin=$OPTARG;;
        u  ) username=$OPTARG;;
        w  ) password=$OPTARG;;
        h  ) usage; exit;;
        \? ) >&2 echo "Unknown option: -$OPTARG"; exit 1;;
        :  ) >&2 echo "Missing option argument for -$OPTARG"; exit 1;;
        *  ) >&2 echo "Unimplemented option: -$OPTARG"; exit 1;;
    esac
done
shift $(($OPTIND - 1))

checkVarSet "stage" "$stage"

case $stage in
    build  ) build;;
    deploy ) deploy;;
    *      ) >&2 echo "Unknown stage: $stage"; exit 1;;
esac
