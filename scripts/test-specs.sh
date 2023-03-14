#!/bin/sh
set -euf
if [ -n "${BASH_VERSION:+x}" ] || [ -n "${ZSH_VERSION:+x}" ]; then
  set -o pipefail
fi

cd bamboo-specs
mvn clean validate verify
