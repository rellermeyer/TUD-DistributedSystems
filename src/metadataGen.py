#!/usr/bin/env python3

import os
import sys
import json
import getopt
import random
from datetime import datetime

# Generate all the metadata on performance for the taskmanager to be used as configs with WASP,
# -n INT: number of task managers
# -c INT: number of configurations
# -s INT: number of slots per task manager

_SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def main(argv):
    numTaskManagers = 1
    numConfigs = 1
    numSlots = 5

    try:
        opts, args = getopt.getopt(sys.argv[1:], 'n:c:s:', ['n=', 'c=', 's='])
    except getopt.GetoptError:
        sys.exit(2)

    for opt, arg in opts:
        if opt == '-n':
            numTaskManagers = int(arg)
        if opt == '-c':
            numConfigs = int(arg)
        if opt == '-s':
            numSlots = int(arg)

    configs = []
    assignedSlots = 0
    for i in range(0, numConfigs):
        data = {}
        # Simulate some random bandwidth and latency
        for i in range(0, numTaskManagers):
            data[str(i)] = {}
            latencies = {}
            bws = {}
            for j in range(0, numTaskManagers):
                if (i != j):
                    latencies[str(j)] = random.uniform(1, 3)
                    bws[str(j)] = random.uniform(500, 3000)
                else:
                    latencies[str(j)] = float(0)
                    # something bigger than the random prRate we generate
                    bws[str(j)] = 999999.0

            data[str(i)]['latencies'] = latencies
            data[str(i)]['bandwidth'] = bws

            # opRate cannot be larger than ipRate
            val1 = random.uniform(1, 1000)
            val2 = random.uniform(1, 500)
            ipRate = max(val1, val2)
            opRate = min(val1, val2)

            data[str(i)]['ipRate'] = ipRate
            data[str(i)]['numSlots'] = numSlots
            prRate = data[str(i)]['prRate'] = random.uniform(1, 1000)
            data[str(i)]['opRate'] = min(prRate, opRate)

        configs.append(data)

        with open(os.path.join(_SCRIPT_DIR, "../src/configs/config-" +
                               datetime.today().strftime("%Y%m%d_%H%M%S") +
                               ".json"), "w") as configFile:
            json.dump(configs, configFile, indent=4, sort_keys=True)


if __name__ == "__main__":
    main(sys.argv[1:])
