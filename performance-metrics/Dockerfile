FROM python:2.7
MAINTAINER CAPS Dev

ENV DATA_DIR=/var/lib/app PSEUDO_ROOT=/host PATH=${PATH}:/opt/app

RUN apt-get update && apt-get install -qy --no-install-recommends librrd-dev

COPY requirements.txt /opt/app/

RUN pip install -r /opt/app/requirements.txt

COPY *.py /opt/app/

WORKDIR /opt/app
CMD ["collect-metrics.py"]
