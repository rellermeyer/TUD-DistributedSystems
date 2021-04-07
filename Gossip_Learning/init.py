#!/usr/bin/python
import sys
import os
import shutil
import random
from itertools import chain

# ["local"] {Eta} {Lambda} {Batchsize} {Label} {Dataset_name} {Model_send_interval}  {Num_nodes} {Num_Neighbours} {Data_aggregation_interval_seconds} [Collector_address]
arguments = sys.argv[1:]
local = False

if arguments[0] == 'local':
    local = True
    arguments.pop(0)

if len(arguments) < 9:
    raise Exception("incorrect commandline arguments")

eta = float(arguments[0])
lambdaa = float(arguments[1])
batch_size = int(arguments[2])
label = arguments[3]
dataset = arguments[4]
model_send_interval = int(arguments[5])
node_count = int(arguments[6])
neighbour_count = int(arguments[7])
data_aggregation_seconds = int(arguments[8])
collector_address = "collector:3000"
if len(arguments) >= 10:
    collector_address = arguments[9]

try:
    shutil.rmtree(f"./dataset-parts")
except OSError:
    print('failed to remove the dataset parts, possibly because they do not yet exist')
os.mkdir(f"./dataset-parts")

with open(f"./datasets/{dataset}/trainSet.csv", "r") as file:
    dataset_parts = [open(f"./dataset-parts/trainSet-{node}.csv", 'w+') for node in range(0, node_count)]
    node = 0
    for row in file:
        dataset_parts[node].write(row)
        node = (node + 1) % node_count

compose_version = 3
collector_image = "neijsvogel/collector:latest"
node_image = "neijsvogel/gossipnode:latest"
memory_clause = """deploy:
            resources:
                limits:
                    memory: 200M
                reservations:
                    memory: 200M"""

if local:
    compose_version = 2
    collector_image = "collector"
    node_image = "gossipnode"
    memory_clause = "mem_limit: 200m"

compose_file = open(("./docker-compose.yml"), "w+")
compose_file.write(f"version: \"{compose_version}\"\n")

if len(arguments) == 9:
    compose_file.write(f"""services:
    collector:
        image: "{collector_image}"
        ports:
            - "8080:3000"
        command: "{data_aggregation_seconds}\"""")
else:
    compose_file.write(f"""services:""")

for i in range(0, int(node_count)):
    neighbour_range = list(chain(range(0, 0 + i), range(i + 1, node_count)))
    neighbours = random.sample(neighbour_range, neighbour_count)
    neighbour_string = " ".join([f"node{neighbour}:4000" for neighbour in neighbours])
    compose_file.write(f"""
    node{i}:
        image: "{node_image}"
        command: "{eta} {lambdaa} {batch_size} {label} {model_send_interval} {collector_address} node{i}:4000 {neighbour_count} {neighbour_string}"
        volumes:
            - ./dataset-parts/trainSet-{i}.csv:/root/trainSet.csv:ro
            - ./datasets/{dataset}/testSet.csv:/root/testSet.csv:ro
        {memory_clause}""")

compose_file.close()
