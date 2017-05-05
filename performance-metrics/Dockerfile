FROM python:2.7
MAINTAINER build-team@atlassian.com

ENV DATA_DIR=/buildeng-metrics PSEUDO_ROOT=/host/ PATH=${PATH}:/opt/app

RUN apt-get update && apt-get install -qy --no-install-recommends \
        librrd-dev \
        curl \
        software-properties-common \
        && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /opt/app/

RUN pip install -r /opt/app/requirements.txt

COPY *.py /opt/app/

WORKDIR /opt/app
CMD ["collect-metrics.py"]
