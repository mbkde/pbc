FROM alpine
MAINTAINER build-team@atlassian.com

ENV DATA_DIR=/buildeng-metrics PSEUDO_ROOT=/host/ PATH=${PATH}:/opt/app

RUN apk add --no-cache py-rrd py-yaml py-pip python
COPY requirements.txt /opt/app/

RUN pip install -r /opt/app/requirements.txt

COPY *.py /opt/app/

WORKDIR /opt/app
CMD ["collect-metrics.py"]
