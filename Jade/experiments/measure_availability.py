import csv
import sys
import time

from common import ping_all_nodes

NUM_CHECKS = 300
PING_INTERVAL = 3
DEFAULT_PORT = 5000


def run_availability_checks(ip_addresses):
    latencies = [['check'] + ip_addresses]
    for i in range(NUM_CHECKS):
        print('Running check iteration', i)

        # noinspection PyTypeChecker
        latencies.append([i] + ping_all_nodes(ip_addresses))
        time.sleep(PING_INTERVAL)

    print(latencies)
    with open('latencies.csv', 'w', newline='') as f:
        csv.writer(f, delimiter=',').writerows(latencies)


if __name__ == '__main__':
    ip_list = sys.argv[1].split(',')
    run_availability_checks(ip_list)
