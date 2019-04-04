import csv
import numpy as np
import sys
import time

from common import ping_all_nodes

TOTAL_TIME = 900
LAMBDA_INTERVAL = 3
DEFAULT_PORT = 5000

np.random.seed(0)
current_time = 0
request_times = []

while current_time < TOTAL_TIME:
    interval = np.random.poisson(LAMBDA_INTERVAL)
    current_time += interval
    request_times.append(interval)


def generate_load(ip_addresses):
    latencies = [['check', 'time'] + ip_addresses]
    current_time = 0
    i = 0
    for interval in request_times:
        print('Running check iteration', i)

        # noinspection PyTypeChecker
        latencies.append([i, current_time] + ping_all_nodes(ip_addresses, '/get-data'))
        time.sleep(interval)
        current_time += interval
        i += 1

    print(latencies)
    with open('latencies.csv', 'w', newline='') as f:
        csv.writer(f, delimiter=',').writerows(latencies)


if __name__ == '__main__':
    ip_list = sys.argv[1].split(',')
    generate_load(ip_list)
