#!/usr/bin/env python

import logging
import os
import rrdtool
import signal
import time
import yaml

from threading import Event

from apscheduler.schedulers.background import BackgroundScheduler


# Data is collected every X seconds
STEP_SIZE_IN_SEC = 5

# Timeout between two updates before the value is considered unknown.
HEARTBEAT_IN_SEC = STEP_SIZE_IN_SEC * 2

PSEUDO_ROOT = os.getenv('PSEUDO_ROOT', '/')  # root directory containing the relevant pseudo files like sys and proc

CPU_DIR = PSEUDO_ROOT + 'cgroup/cpu/docker'
CPU_ACC_DIR = PSEUDO_ROOT + 'cgroup/cpuacct/docker'
MEM_DIR = PSEUDO_ROOT + 'cgroup/memory/docker'

DATA_DIR = os.getenv('DATA_DIR', '/buildeng-metrics')

# How long to keep metrics in the database, based on the parameter above
#
# 12 samples per minute
# 720 samples per hour
# 1440 samples in two hours
ROUND_ROBIN_ARCHIVES = [
    'RRA:MIN:0.5:1:1440',  # keep every sample collected within the last two hours
    'RRA:MIN:0.5:12:360',  # keep an average value per minute for the last six hours
    'RRA:MAX:0.5:1:1440',
    'RRA:MAX:0.5:12:360',
    'RRA:AVERAGE:0.5:1:1440',
    'RRA:AVERAGE:0.5:12:360',
]

DATA_SOURCES = {
    'cpu.usage': {
        'datasource': 'DS:%s:COUNTER:%d:0:U',
        'fields': [
            'user',
            'system',
            'throttled',
        ],
    },
    'blkio.usage': {
        'datasource': 'DS:%s:COUNTER:%d:0:U',
        'fields': [
            'read_bytes',
            'read_ops',
            'write_bytes',
            'write_ops',
            'sync_bytes',
            'sync_ops',
            'async_bytes',
            'async_ops',
            'total_bytes',
            'total_ops',
        ],
    },
    'memory.usage': {
        'datasource': 'DS:%s:GAUGE:%d:0:U',
        'fields': [
            'cache',
            'rss',
            'swap',
            'total',
            'limit',
        ],
    },
    'network.usage': {
        'datasource': 'DS:%s:COUNTER:%d:0:U',
        'fields': [
            'rx_bytes',
            'rx_packets',
            'rx_errors',
            'rx_dropped',
            'tx_bytes',
            'tx_packets',
            'tx_errors',
            'tx_dropped',
        ],
    },
}


redPill = Event()
scheduler = BackgroundScheduler()


def parse_pseudo_file(pseudo_file):
    if not os.path.exists(pseudo_file):
        logging.info('Device file not found, ignoring: %s', pseudo_file)
        return {}

    with open(pseudo_file, 'r') as f:
        lines = f.read().splitlines()

    metrics = dict()

    for line in lines:
        data = line.split()
        metrics[data[0]] = int(data[1])

    return metrics


def parse_single_value_pseudo_file(pseudo_file, default=None):
    """A file containing just one single value"""
    if not os.path.exists(pseudo_file):
        logging.info('Device file not found, ignoring: %s', pseudo_file)
        return default

    with open(pseudo_file, 'r') as f:
        return f.read().strip()


def memory(container_name):
    usage = parse_pseudo_file(os.path.join(MEM_DIR, container_name, 'memory.stat'))
    usage['total'] = int(usage.get('cache', 0)) + int(usage.get('rss', 0)) + int(usage.get('swap', 0))
    usage['limit'] = parse_single_value_pseudo_file(os.path.join(MEM_DIR, container_name, 'memory.limit_in_bytes'), 0)
    # if there is no limit, this value is crazy long number
    if int(usage['limit']) > 100000 * 1024 * 1024:
        usage['limit'] = 0

    return usage


def cpu(container_name):
    throttled = parse_pseudo_file(os.path.join(CPU_DIR, container_name, 'cpu.stat'))
    usage = parse_pseudo_file(os.path.join(CPU_ACC_DIR, container_name, 'cpuacct.stat'))
    usage['throttled'] = int(int(throttled.get('throttled_time', 0)) * 0.0000001)  # thottled in ns, need to align with CPU which is in 10-millisecond

    return usage

def create_database_file(output_file, config):
    logging.info('Creating RRD database %s', output_file)
    data_sources = [config['datasource'] % (field, HEARTBEAT_IN_SEC) for field in config['fields']]
    rrdtool.create(output_file, '--step', str(STEP_SIZE_IN_SEC), '--start', 'now', data_sources, ROUND_ROBIN_ARCHIVES)


def dispatch_data(data, value_type, container_data_path):
    if data is not None and len(data) > 0:
        output_file =  os.path.join(container_data_path, '%s.rrd' % (value_type))
        config = DATA_SOURCES.get(value_type)
        if not config:
            logging.warning('No matching data source found for type %s, please report this to the developer', value_type)
            return

        if not os.path.exists(output_file):
            create_database_file(output_file, config)

        values = [str(data.get(field, 0)) for field in config['fields']]
        rrdtool.update(output_file, 'N:' + ':'.join(values))

def update_metadata(ctx):
    with open(os.path.join(DATA_DIR, 'metadata.yaml'), 'w') as f:
        yaml.dump({'start_epoch_time': ctx.get('start_epoch_time', ''), 'end_epoch_time': int(time.time())}, f)


def report_metrics_callback(ctx):
    logging.info('Collecting metrics')

    docker_containers = [f for f in os.listdir(CPU_DIR) if not os.path.isfile(os.path.join(CPU_DIR, f))]
    for container in docker_containers:
        container_data_path = os.path.join(DATA_DIR, container)
        if not os.path.exists(container_data_path):
            os.makedirs(container_data_path)
        if not os.path.isfile(os.path.join(container_data_path, 'arn')):
            # TODO: eventually only write the results below for valid results
            # TODO: threadify this
            # TODO: update_metadata(ctx) we need to update each separately with start time on discovery
            os.system('docker inspect --format \'{{index .Config.Labels "com.amazonaws.ecs.task-arn"}}||{{index .Config.Labels "com.amazonaws.ecs.container-name"}}\' ' + container + ' > ' + container_data_path + '/arn')
        else:
            if not os.path.isfile(os.path.join(container_data_path, 'stop')):
                dispatch_data(cpu(container), 'cpu.usage', container_data_path)
                dispatch_data(memory(container), 'memory.usage', container_data_path)
    update_metadata(ctx)


def wait_for_kill_signal(signal, f):
    scheduler.shutdown()
    redPill.set()

if __name__ == '__main__':
    logging.basicConfig(format='%(asctime)s - %(levelname)s - %(message)s', level=logging.INFO)
    logging.getLogger("apscheduler.executors.default").setLevel(logging.WARN)

    ctx = {'start_epoch_time': int(time.time())}

    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)

    # Start the reporting
    scheduler.start()
    scheduler.add_job(report_metrics_callback, 'interval', [ctx], seconds=STEP_SIZE_IN_SEC, coalesce=True, max_instances=1)

    # loop over all docker containers in the /cgroup/cpu/ dir,
    # collect the cpu.stat file for each one

    # Go to sleep
    signal.signal(signal.SIGINT, wait_for_kill_signal)
    redPill.wait()
    logging.info("Bye")
