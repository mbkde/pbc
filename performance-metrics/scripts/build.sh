#!/bin/bash
set -euf
if [ -n "${BASH_VERSION:+x}" -o -n "${ZSH_VERSION:+x}" ]; then
  set -o pipefail
fi

docker build -t "performance-metrics:development" .
