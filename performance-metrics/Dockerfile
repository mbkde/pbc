FROM python:2.7
MAINTAINER build-team@atlassian.com

# docker run -v /cgroup/:/host/cgroup/ -v /var/lib/docker/buildeng-metrics:/buildeng-metrics <image>
ENV DATA_DIR=/buildeng-metrics PSEUDO_ROOT=/host/ PATH=${PATH}:/opt/app

RUN apt-get update && apt-get install -qy --no-install-recommends librrd-dev

COPY requirements.txt /opt/app/

RUN pip install -r /opt/app/requirements.txt

COPY *.py /opt/app/

WORKDIR /opt/app
CMD ["collect-metrics.py"]
