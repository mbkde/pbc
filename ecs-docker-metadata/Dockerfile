FROM docker.atlassian.io/buildeng/agent-baseagent

ADD get_metadata.sh /get_metadata.sh

ENV DOCKER_API_VERSION 1.23
CMD [ "/get_metadata.sh" ]
ENTRYPOINT [ "/bin/sh" ]
