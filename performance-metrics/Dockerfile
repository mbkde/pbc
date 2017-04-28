FROM python:2.7
MAINTAINER build-team@atlassian.com

ENV DATA_DIR=/buildeng-metrics PSEUDO_ROOT=/host/ PATH=${PATH}:/opt/app

RUN apt-get update && apt-get install -qy --no-install-recommends \
        librrd-dev \
        apt-transport-https \
        ca-certificates \
        curl \
        software-properties-common \
        && curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add - \
        && apt-key fingerprint 0EBFCD88 \
        && add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian jessie stable" \
        && apt-get update \
        && apt-get install -qy docker-ce \
        && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /opt/app/

RUN pip install -r /opt/app/requirements.txt

COPY *.py /opt/app/

WORKDIR /opt/app
CMD ["collect-metrics.py"]
