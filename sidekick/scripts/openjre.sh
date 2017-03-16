#!/bin/bash

docker pull openjdk:8-jre
docker run --rm -t \
    -v "$(pwd):$(pwd)" \
    -w "$(pwd)" \
    --entrypoint "/bin/bash" \
    openjdk:8-jre \
    -c 'tar -cvzhf $(pwd)/openjre.tar.gz -C /usr/lib/jvm/java-8-openjdk-amd64 jre'
