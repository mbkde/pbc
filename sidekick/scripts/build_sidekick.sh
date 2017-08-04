#!/bin/bash
set -euf
if [ -n "${BASH_VERSION:+x}" -o -n "${ZSH_VERSION:+x}" ]; then
  set -o pipefail
fi

# Manage CLI options
usage() {
  >&2 cat <<USAGE
Usage: $0 [OPTION]...
Creates a sidekick.

Mandatory arguments to long options are mandatory for short options too.
  -j,    Java (JRE) download url or path inside working directory
  -b,    Bamboo version
  -B,    Bamboo instance for pre-warming the agent (optional)
  -d,    Debug mode.

Environment variables BAMBOO_USER and BAMBOO_PASSWORD are used when -B is specified
as credentials to remote the prewarmed agent definition on the bamboo instance
USAGE
}

download_if_not_cached() {
  filename="$1"
  url="$2"
  checksum="${3:-}"
  if [ ! "`find 'output/' -maxdepth 1 -name "${filename}" -ctime -1`" ]; then
    if [ -f "$url" ]; then
        cp "$url" "output/${filename}"
    else
        wget -O "output/${filename}" --progress=dot:mega "${url}"
    fi
  fi
  if [ -n "${checksum}" ]; then
    echo "${checksum} output/${filename}" | sha256sum -c
  fi
}

if [ -z "${BAMBOO_USER:=}" ]; then
    BAMBOO_USER=
fi
if [ -z "${BAMBOO_PASSWORD:=}" ]; then
    BAMBOO_PASSWORD=
fi

# TODO we need a docker image with docker/wget/bash inside it.
if [ ! -f '/.dockerenv' ]; then
  server_version=`docker version --format '{{.Server.APIVersion}}'`
  docker run --rm -t \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$(pwd):$(pwd)" \
    -w "$(pwd)" \
    -e "DOCKER_API_VERSION=${server_version}" \
    -e "BAMBOO_USER=${BAMBOO_USER}" \
    -e "BAMBOO_PASSWORD=${BAMBOO_PASSWORD}" \
    --entrypoint "$0" \
    mkleint/sidekick-builder \
    "$@"
  exit
fi

debug=false
bamboo_version=''
bamboo_instance=''
jre_url=''
volume_path='output/volume'
while getopts ':db:B:j:h' option; do
  case $option in
    d  ) debug=true;;
    j  ) jre_url=$OPTARG;;
    b  ) bamboo_version=$OPTARG;;
    B  ) bamboo_instance=$OPTARG;;
    h  ) usage; exit;;
    \? ) >&2 echo "Unknown option: -$OPTARG"; exit 1;;
    :  ) >&2 echo "Missing option argument for -$OPTARG"; exit 1;;
    *  ) >&2 echo "Unimplemented option: -$OPTARG"; exit 1;;
  esac
done
shift $(($OPTIND - 1))

# Enable options
if $debug; then
  set -x
fi
if [ -z "${bamboo_version}" ]; then
  >&2 echo 'Bamboo version not specified, specify it with "-b"'
  >&2 echo ' '
  usage
  exit 1
fi

if [ -z "${jre_url}" ]; then
  >&2 echo 'Url to JRE download not specified, declare it with  "-j"'
  >&2 echo ' '
  usage
  exit 1
fi

mkdir -p "${volume_path}"

################################
# Download and install the JRE #
################################
download_if_not_cached jre.tar.gz "${jre_url}"
mkdir -p "${volume_path}/jre"
tar --no-same-owner -xzvf output/jre.tar.gz --strip 1 -C "${volume_path}/jre"
export PATH="$(pwd)/${volume_path}/jre/bin:$PATH"

###################################
# Download and pre-warm the agent #
###################################
download_if_not_cached atlassian-bamboo-agent.jar "https://packages.atlassian.com/maven/repository/public/com/atlassian/bamboo/bamboo-agent/${bamboo_version}/bamboo-agent-${bamboo_version}.jar"
cp 'output/atlassian-bamboo-agent.jar' "${volume_path}"
# Pre-warm the agent if we know the instance
mkdir -p output/bamboo-agent-home
if [ -n "$bamboo_instance" ]; then
  ./scripts/update-agent.sh output/bamboo-agent-home $bamboo_instance
fi

############################
# Install additional tools #
############################
# Capability management
cp 'files/bamboo-update-capability' "${volume_path}"
# Agent startup
cp 'files/run-agent.sh' "${volume_path}"

# JQ
download_if_not_cached jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64
cp 'output/jq' "${volume_path}"
chmod +x "${volume_path}/jq"

#####################
# Burn the Sidekick #
#####################
# TODO don't hardcode image/tag
docker build -f files/Dockerfile -t "bamboo-agent-sidekick:development" .
