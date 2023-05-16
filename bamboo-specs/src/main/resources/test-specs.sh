#!/bin/sh
 set -eufx

 export JAVA_HOME="${bamboo.capability.system.jdk.JDK 11}"
 export PATH="${bamboo.capability.system.jdk.JDK 11}/bin:$PATH"

 scripts/test-specs.sh
