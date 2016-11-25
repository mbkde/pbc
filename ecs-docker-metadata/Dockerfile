FROM docker:1.11
#1.11 is what latest ecs ami runs

ADD get_metadata.sh /get_metadata.sh

RUN apk add --no-cache jq

CMD [ "/get_metadata.sh" ]
ENTRYPOINT [ "/bin/sh" ]
